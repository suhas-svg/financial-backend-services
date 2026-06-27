package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonthlyStatementServiceTest {

    @Mock private CustomerMonthlyStatementRepository statementRepository;
    @Mock private CustomerMonthlyStatementLineRepository lineRepository;
    @Mock private LedgerAccountRepository ledgerAccountRepository;
    @Mock private JournalTransactionRepository journalRepository;
    @Mock private JournalPostingRepository postingRepository;
    @Mock private JournalStateEventRepository stateEventRepository;

    private MonthlyStatementService service;

    @BeforeEach
    void setUp() {
        service = new MonthlyStatementService(
                statementRepository,
                lineRepository,
                ledgerAccountRepository,
                journalRepository,
                postingRepository,
                stateEventRepository);
    }

    @Test
    void generateMonthlyStatementUsesUtcMonthBoundariesPostedLinesAndStableIdempotency() {
        UUID accountId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        LedgerAccount account = ledgerAccount(accountId, "customer-1", "1001", "USD");
        YearMonth period = YearMonth.of(2026, 5);
        LocalDate periodStart = LocalDate.of(2026, 5, 1);
        LocalDate periodEnd = LocalDate.of(2026, 6, 1);

        JournalTransaction opening = journal("11111111-1111-1111-1111-111111111111", LocalDate.of(2026, 4, 30), "Opening");
        JournalTransaction debit = journal("22222222-2222-2222-2222-222222222222", LocalDate.of(2026, 5, 10), "ATM withdrawal");
        JournalTransaction pendingCredit = journal("33333333-3333-3333-3333-333333333333", LocalDate.of(2026, 5, 11), "Pending credit");
        JournalTransaction feeRefund = journal("44444444-4444-4444-4444-444444444444", LocalDate.of(2026, 5, 12), "Fee refund");
        JournalTransaction lateCorrection = journal("55555555-5555-5555-5555-555555555555", LocalDate.of(2026, 6, 1), "Late correction");

        when(statementRepository.findLatestByOwnerAndAccountAndPeriod("customer-1", "1001", periodStart, periodEnd))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingStatement(accountId, periodStart, periodEnd)));
        when(ledgerAccountRepository.findByExternalAccountId("1001")).thenReturn(Optional.of(account));
        when(journalRepository.findAllByEffectiveDateLessThan(periodEnd))
                .thenReturn(List.of(opening, debit, pendingCredit, feeRefund));
        when(stateEventRepository.findFirstByJournalIdOrderByEventSequenceDesc(opening.getJournalId()))
                .thenReturn(Optional.of(state(opening.getJournalId(), JournalState.POSTED)));
        when(stateEventRepository.findFirstByJournalIdOrderByEventSequenceDesc(debit.getJournalId()))
                .thenReturn(Optional.of(state(debit.getJournalId(), JournalState.POSTED)));
        when(stateEventRepository.findFirstByJournalIdOrderByEventSequenceDesc(pendingCredit.getJournalId()))
                .thenReturn(Optional.of(state(pendingCredit.getJournalId(), JournalState.PENDING)));
        when(stateEventRepository.findFirstByJournalIdOrderByEventSequenceDesc(feeRefund.getJournalId()))
                .thenReturn(Optional.of(state(feeRefund.getJournalId(), JournalState.POSTED)));
        when(postingRepository.findByJournalIdOrderByPostingSequence(opening.getJournalId()))
                .thenReturn(List.of(posting(opening.getJournalId(), accountId, PostingDirection.CREDIT, "1000.00")));
        when(postingRepository.findByJournalIdOrderByPostingSequence(debit.getJournalId()))
                .thenReturn(List.of(posting(debit.getJournalId(), accountId, PostingDirection.DEBIT, "25.00")));
        when(postingRepository.findByJournalIdOrderByPostingSequence(feeRefund.getJournalId()))
                .thenReturn(List.of(posting(feeRefund.getJournalId(), accountId, PostingDirection.CREDIT, "10.00")));
        when(statementRepository.save(any(CustomerMonthlyStatement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lineRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerMonthlyStatementResult generated = service.generate("customer-1", "1001", period);
        CustomerMonthlyStatementResult replay = service.generate("customer-1", "1001", period);

        assertThat(generated.periodStart()).isEqualTo(periodStart);
        assertThat(generated.periodEnd()).isEqualTo(periodEnd);
        assertThat(generated.openingBalance()).isEqualByComparingTo("1000.00");
        assertThat(generated.closingBalance()).isEqualByComparingTo("985.00");
        assertThat(generated.lines()).extracting(CustomerMonthlyStatementLineResult::description)
                .containsExactly("ATM withdrawal", "Fee refund");
        assertThat(generated.lines()).extracting(CustomerMonthlyStatementLineResult::amount)
                .containsExactly(new BigDecimal("-25.00"), new BigDecimal("10.00"));
        assertThat(replay.statementId()).isEqualTo(UUID.fromString("99999999-9999-9999-9999-999999999999"));

        verify(journalRepository, never()).findAllByEffectiveDateLessThan(LocalDate.of(2026, 6, 2));
        verify(postingRepository, never()).findByJournalIdOrderByPostingSequence(lateCorrection.getJournalId());
        verify(statementRepository, times(1)).save(any(CustomerMonthlyStatement.class));
    }

    private LedgerAccount ledgerAccount(UUID accountId, String ownerId, String externalAccountId, String currency) {
        return LedgerAccount.builder()
                .ledgerAccountId(accountId)
                .accountKind(LedgerAccountKind.CUSTOMER)
                .ownerId(ownerId)
                .externalAccountId(externalAccountId)
                .currency(currency)
                .status(LedgerAccountStatus.ACTIVE)
                .createdAt(LocalDateTime.parse("2026-01-01T00:00:00"))
                .build();
    }

    private JournalTransaction journal(String id, LocalDate effectiveDate, String description) {
        return JournalTransaction.builder()
                .journalId(UUID.fromString(id))
                .journalReference("JRN-" + id.substring(0, 8))
                .journalType(JournalType.TRANSFER)
                .currency("USD")
                .effectiveDate(effectiveDate)
                .description(description)
                .correlationId(id)
                .createdBy("system")
                .createdAt(effectiveDate.atStartOfDay())
                .idempotencyScope("test")
                .idempotencyKey(id)
                .requestFingerprint(id)
                .build();
    }

    private JournalStateEvent state(UUID journalId, JournalState state) {
        return JournalStateEvent.builder()
                .eventId(UUID.randomUUID())
                .journalId(journalId)
                .eventSequence(2)
                .state(state)
                .actor("system")
                .createdAt(LocalDateTime.parse("2026-05-01T00:00:00"))
                .build();
    }

    private JournalPosting posting(UUID journalId, UUID accountId, PostingDirection direction, String amount) {
        return JournalPosting.builder()
                .postingId(UUID.randomUUID())
                .journalId(journalId)
                .ledgerAccountId(accountId)
                .postingSequence(1)
                .direction(direction)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .build();
    }

    private CustomerMonthlyStatement existingStatement(UUID accountId, LocalDate periodStart, LocalDate periodEnd) {
        return CustomerMonthlyStatement.builder()
                .statementId(UUID.fromString("99999999-9999-9999-9999-999999999999"))
                .ledgerAccountId(accountId)
                .ownerId("customer-1")
                .externalAccountId("1001")
                .currency("USD")
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .statementVersion(1)
                .openingBalance(new BigDecimal("1000.00"))
                .closingBalance(new BigDecimal("985.00"))
                .generatedAt(LocalDateTime.parse("2026-06-01T00:00:00"))
                .build();
    }
}
