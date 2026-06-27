package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.JournalPostingRepository;
import com.suhasan.finance.transaction_service.ledger.repository.JournalStateEventRepository;
import com.suhasan.finance.transaction_service.ledger.repository.JournalTransactionRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCustomerLedgerQueryServiceTest {

    @Mock private LedgerAccountRepository accountRepository;
    @Mock private LedgerBalanceProjectionRepository projectionRepository;
    @Mock private JournalTransactionRepository journalRepository;
    @Mock private JournalPostingRepository postingRepository;
    @Mock private JournalStateEventRepository stateEventRepository;

    private DefaultCustomerLedgerQueryService service;

    @BeforeEach
    void setUp() {
        service = new DefaultCustomerLedgerQueryService(
                accountRepository, projectionRepository, journalRepository, postingRepository, stateEventRepository);
    }

    @Test
    void listAccountsReturnsCustomerOwnedProjectionsInExternalAccountOrder() {
        LedgerAccount accountA = customerAccount("1001", "customer-1");
        LedgerAccount accountB = customerAccount("2002", "customer-1");
        LedgerBalanceProjection projectionA = LedgerBalanceProjection.open(
                accountA.getLedgerAccountId(), new BigDecimal("125.00"));
        projectionA.reserveDebit(new BigDecimal("25.00"), 1L);
        LedgerBalanceProjection projectionB = LedgerBalanceProjection.open(
                accountB.getLedgerAccountId(), new BigDecimal("50.00"));
        when(accountRepository.findByOwnerIdAndAccountKindOrderByExternalAccountIdAsc(
                "customer-1", LedgerAccountKind.CUSTOMER)).thenReturn(List.of(accountA, accountB));
        when(projectionRepository.findAllById(List.of(accountA.getLedgerAccountId(), accountB.getLedgerAccountId())))
                .thenReturn(List.of(projectionB, projectionA));

        List<LedgerAccountSummaryResponse> response = service.listAccounts("customer-1");

        assertThat(response).extracting(LedgerAccountSummaryResponse::externalAccountId)
                .containsExactly("1001", "2002");
        assertThat(response.getFirst().postedBalance()).isEqualByComparingTo("125.00");
        assertThat(response.getFirst().pendingBalance()).isEqualByComparingTo("-25.00");
        assertThat(response.getFirst().availableBalance()).isEqualByComparingTo("100.00");
        assertThat(response.getFirst().projectionVersion()).isEqualTo(1L);
    }

    @Test
    void getBalanceHidesAccountsOwnedByOtherCustomers() {
        LedgerAccount otherCustomersAccount = customerAccount("other-account", "customer-2");
        when(accountRepository.findByExternalAccountId("other-account"))
                .thenReturn(Optional.of(otherCustomersAccount));

        assertThatThrownBy(() -> service.getBalance("customer-1", "other-account"))
                .isInstanceOf(LedgerAccountNotFoundException.class);
    }

    @Test
    void getBalancesPreservesRequestedOrderAndHidesUnownedAccounts() {
        LedgerAccount accountA = customerAccount("1001", "customer-1");
        LedgerAccount accountB = customerAccount("2002", "customer-1");
        LedgerBalanceProjection projectionA = LedgerBalanceProjection.open(
                accountA.getLedgerAccountId(), new BigDecimal("125.00"));
        LedgerBalanceProjection projectionB = LedgerBalanceProjection.open(
                accountB.getLedgerAccountId(), new BigDecimal("50.00"));
        when(accountRepository.findByExternalAccountId("2002")).thenReturn(Optional.of(accountB));
        when(accountRepository.findByExternalAccountId("1001")).thenReturn(Optional.of(accountA));
        when(projectionRepository.findById(accountB.getLedgerAccountId())).thenReturn(Optional.of(projectionB));
        when(projectionRepository.findById(accountA.getLedgerAccountId())).thenReturn(Optional.of(projectionA));

        List<LedgerAccountSummaryResponse> response = service.getBalances("customer-1", List.of("2002", "1001"));

        assertThat(response).extracting(LedgerAccountSummaryResponse::externalAccountId)
                .containsExactly("2002", "1001");
    }

    @Test
    void getJournalReturnsOnlyCustomerOwnedPostingsAndRedactsSystemAccounts() {
        UUID journalId = UUID.randomUUID();
        LedgerAccount customer = customerAccount("1001", "customer-1");
        LedgerAccount clearing = systemAccount(LedgerAccountKind.CLEARING);
        JournalTransaction journal = journal(journalId);
        JournalPosting customerPosting = posting(journalId, customer.getLedgerAccountId(), 1, PostingDirection.DEBIT);
        JournalPosting clearingPosting = posting(journalId, clearing.getLedgerAccountId(), 2, PostingDirection.CREDIT);
        when(journalRepository.findById(journalId)).thenReturn(Optional.of(journal));
        when(postingRepository.findByJournalIdOrderByPostingSequence(journalId))
                .thenReturn(List.of(customerPosting, clearingPosting));
        when(accountRepository.findAllById(any())).thenReturn(List.of(customer, clearing));
        when(stateEventRepository.findFirstByJournalIdOrderByEventSequenceDesc(journalId))
                .thenReturn(Optional.of(state(journalId, JournalState.POSTED)));

        CustomerJournalResponse response = service.getJournal("customer-1", journalId);

        assertThat(response.journalId()).isEqualTo(journalId);
        assertThat(response.state()).isEqualTo("POSTED");
        assertThat(response.customerAmount()).isEqualByComparingTo("25.00");
        assertThat(response.postings()).hasSize(1);
        assertThat(response.postings().getFirst().externalAccountId()).isEqualTo("1001");
    }

    @Test
    void getJournalReturnsNotFoundWhenJournalHasNoPostingOwnedByCustomer() {
        UUID journalId = UUID.randomUUID();
        LedgerAccount otherCustomer = customerAccount("2002", "customer-2");
        JournalTransaction journal = journal(journalId);
        when(journalRepository.findById(journalId)).thenReturn(Optional.of(journal));
        when(postingRepository.findByJournalIdOrderByPostingSequence(journalId))
                .thenReturn(List.of(posting(journalId, otherCustomer.getLedgerAccountId(), 1, PostingDirection.DEBIT)));
        when(accountRepository.findAllById(any())).thenReturn(List.of(otherCustomer));

        assertThatThrownBy(() -> service.getJournal("customer-1", journalId))
                .isInstanceOf(LedgerAccountNotFoundException.class);
    }

    private LedgerAccount customerAccount(String externalAccountId, String ownerId) {
        return LedgerAccount.builder()
                .ledgerAccountId(UUID.randomUUID())
                .accountKind(LedgerAccountKind.CUSTOMER)
                .currency("USD")
                .externalAccountId(externalAccountId)
                .ownerId(ownerId)
                .status(LedgerAccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private LedgerAccount systemAccount(LedgerAccountKind kind) {
        return LedgerAccount.builder()
                .ledgerAccountId(UUID.randomUUID())
                .accountKind(kind)
                .currency("USD")
                .status(LedgerAccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private JournalTransaction journal(UUID journalId) {
        return JournalTransaction.builder()
                .journalId(journalId)
                .journalReference("JRN-" + journalId)
                .journalType(JournalType.TRANSFER)
                .currency("USD")
                .effectiveDate(LocalDate.now())
                .description("Transfer to savings")
                .correlationId("correlation-1")
                .createdBy("customer-1")
                .createdAt(LocalDateTime.parse("2026-06-25T03:35:00"))
                .idempotencyScope("customer-1:TRANSFER")
                .idempotencyKey("idem-1")
                .requestFingerprint("fingerprint-1")
                .build();
    }

    private JournalPosting posting(UUID journalId, UUID accountId, int sequence, PostingDirection direction) {
        return JournalPosting.builder()
                .postingId(UUID.randomUUID())
                .journalId(journalId)
                .ledgerAccountId(accountId)
                .postingSequence(sequence)
                .direction(direction)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .memo("Transfer to savings")
                .build();
    }

    private JournalStateEvent state(UUID journalId, JournalState state) {
        return JournalStateEvent.builder()
                .eventId(UUID.randomUUID())
                .journalId(journalId)
                .eventSequence(2)
                .state(state)
                .actor("SYSTEM")
                .createdAt(LocalDateTime.parse("2026-06-25T03:36:00"))
                .build();
    }
}
