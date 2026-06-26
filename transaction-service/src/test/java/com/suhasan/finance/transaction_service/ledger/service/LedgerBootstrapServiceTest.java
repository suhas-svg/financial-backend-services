package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerBootstrapServiceTest {

    @Mock private LedgerBootstrapAccountSource accountSource;
    @Mock private LedgerAccountRepository accountRepository;
    @Mock private LedgerBalanceProjectionRepository projectionRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private LedgerPostingService postingService;

    private LedgerBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new LedgerBootstrapService(
                accountSource,
                accountRepository,
                projectionRepository,
                transactionRepository,
                postingService,
                new SimpleMeterRegistry());
    }

    @Test
    void bootstrapRequiresExplicitEnablementAndMaintenanceMode() {
        assertThatThrownBy(() -> service.bootstrap(LedgerBootstrapCommand.disabled("operator")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");

        assertThatThrownBy(() -> service.bootstrap(LedgerBootstrapCommand.enabled("operator", false, LocalDate.parse("2026-06-26"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maintenance mode");

        verifyNoInteractions(accountSource, accountRepository, projectionRepository, postingService);
    }

    @Test
    void bootstrapRefusesUnresolvedLegacyHoldsAndProcessingTransactionsBeforeImport() {
        when(accountSource.fetchAccountsForBootstrap()).thenReturn(List.of(
                account("1001", "customer-1", "USD", "100.00", "80.00", "ACTIVE")));
        when(transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PROCESSING))
                .thenReturn(List.of(processingTransaction()));

        assertThatThrownBy(() -> service.bootstrap(LedgerBootstrapCommand.enabled("operator", true, LocalDate.parse("2026-06-26"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unresolved legacy holds")
                .hasMessageContaining("processing transactions");

        verifyNoInteractions(accountRepository, projectionRepository, postingService);
    }

    @Test
    void bootstrapSeedsSystemAccountsAndPostsBalancedOpeningJournalsPerCurrency() {
        LedgerBootstrapAccountSnapshot usdCustomer = account("1001", "customer-1", "USD", "100.00", "100.00", "ACTIVE");
        LedgerBootstrapAccountSnapshot eurCustomer = account("2001", "customer-2", "EUR", "-25.00", "-25.00", "ACTIVE");
        when(accountSource.fetchAccountsForBootstrap()).thenReturn(List.of(usdCustomer, eurCustomer));
        when(transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PROCESSING)).thenReturn(List.of());
        when(transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PENDING)).thenReturn(List.of());
        when(accountRepository.findByAccountKindAndCurrency(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByExternalAccountId(any())).thenReturn(Optional.empty());
        when(accountRepository.save(any(LedgerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectionRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(projectionRepository.save(any(LedgerBalanceProjection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postingService.createPending(any(JournalCommand.class))).thenAnswer(invocation -> {
            JournalCommand command = invocation.getArgument(0);
            return new JournalResult(UUID.nameUUIDFromBytes(command.idempotencyKey().getBytes()), JournalState.PENDING, false);
        });
        when(postingService.post(any(UUID.class), eq("operator"))).thenAnswer(invocation ->
                new JournalResult(invocation.getArgument(0), JournalState.POSTED, false));

        LedgerBootstrapResult result = service.bootstrap(LedgerBootstrapCommand.enabled("operator", true, LocalDate.parse("2026-06-26")));

        assertThat(result.importedAccounts()).isEqualTo(2);
        assertThat(result.seededSystemAccounts()).isEqualTo(6);
        assertThat(result.openingJournals()).isEqualTo(2);
        assertThat(result.currencies()).containsExactlyInAnyOrder("USD", "EUR");

        ArgumentCaptor<LedgerAccount> accountCaptor = ArgumentCaptor.forClass(LedgerAccount.class);
        verify(accountRepository, times(8)).save(accountCaptor.capture());
        assertThat(accountCaptor.getAllValues())
                .extracting(LedgerAccount::getAccountKind, LedgerAccount::getCurrency)
                .contains(
                        tuple(LedgerAccountKind.CLEARING, "USD"),
                        tuple(LedgerAccountKind.SUSPENSE, "USD"),
                        tuple(LedgerAccountKind.FEE, "USD"),
                        tuple(LedgerAccountKind.CLEARING, "EUR"),
                        tuple(LedgerAccountKind.SUSPENSE, "EUR"),
                        tuple(LedgerAccountKind.FEE, "EUR"),
                        tuple(LedgerAccountKind.CUSTOMER, "USD"),
                        tuple(LedgerAccountKind.CUSTOMER, "EUR"));

        ArgumentCaptor<JournalCommand> commandCaptor = ArgumentCaptor.forClass(JournalCommand.class);
        verify(postingService, times(2)).createPending(commandCaptor.capture());
        assertThat(commandCaptor.getAllValues())
                .extracting(JournalCommand::journalType, JournalCommand::currency, JournalCommand::idempotencyScope)
                .containsExactly(
                        tuple(JournalType.OPENING_BALANCE, "USD", "LEDGER_BOOTSTRAP:2026-06-26"),
                        tuple(JournalType.OPENING_BALANCE, "EUR", "LEDGER_BOOTSTRAP:2026-06-26"));
        assertThat(commandCaptor.getAllValues().get(0).postings())
                .extracting(PostingDraft::direction, PostingDraft::amount)
                .containsExactly(tuple(PostingDirection.DEBIT, new BigDecimal("100.00")), tuple(PostingDirection.CREDIT, new BigDecimal("100.00")));
        assertThat(commandCaptor.getAllValues().get(1).postings())
                .extracting(PostingDraft::direction, PostingDraft::amount)
                .containsExactly(tuple(PostingDirection.DEBIT, new BigDecimal("25.00")), tuple(PostingDirection.CREDIT, new BigDecimal("25.00")));
    }

    @Test
    void bootstrapIsIdempotentWhenAccountProjectionAlreadyMatchesLegacyMirror() {
        LedgerAccount existing = customerAccount("1001", "customer-1", "USD");
        when(accountSource.fetchAccountsForBootstrap()).thenReturn(List.of(
                account("1001", "customer-1", "USD", "100.00", "100.00", "ACTIVE")));
        when(transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PROCESSING)).thenReturn(List.of());
        when(transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PENDING)).thenReturn(List.of());
        when(accountRepository.findByAccountKindAndCurrency(any(), eq("USD"))).thenReturn(Optional.of(systemAccount(LedgerAccountKind.CLEARING, "USD")));
        when(accountRepository.findByExternalAccountId("1001")).thenReturn(Optional.of(existing));
        when(projectionRepository.findById(existing.getLedgerAccountId()))
                .thenReturn(Optional.of(LedgerBalanceProjection.open(existing.getLedgerAccountId(), new BigDecimal("100.00"))));

        LedgerBootstrapResult result = service.bootstrap(LedgerBootstrapCommand.enabled("operator", true, LocalDate.parse("2026-06-26")));

        assertThat(result.reusedAccounts()).isEqualTo(1);
        assertThat(result.openingJournals()).isZero();
        verify(accountRepository, never()).save(any(LedgerAccount.class));
        verifyNoInteractions(postingService);
    }

    private LedgerBootstrapAccountSnapshot account(
            String accountId,
            String ownerId,
            String currency,
            String ledgerBalance,
            String availableBalance,
            String status) {
        return new LedgerBootstrapAccountSnapshot(
                accountId,
                ownerId,
                currency,
                new BigDecimal(ledgerBalance),
                new BigDecimal(availableBalance),
                status);
    }

    private Transaction processingTransaction() {
        return Transaction.builder()
                .transactionId("tx-processing")
                .status(TransactionStatus.PROCESSING)
                .createdBy("customer-1")
                .fromAccountId("1001")
                .toAccountId("2001")
                .amount(BigDecimal.TEN)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private LedgerAccount customerAccount(String externalAccountId, String ownerId, String currency) {
        return LedgerAccount.builder()
                .ledgerAccountId(UUID.randomUUID())
                .accountKind(LedgerAccountKind.CUSTOMER)
                .externalAccountId(externalAccountId)
                .ownerId(ownerId)
                .currency(currency)
                .status(LedgerAccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private LedgerAccount systemAccount(LedgerAccountKind kind, String currency) {
        return LedgerAccount.builder()
                .ledgerAccountId(UUID.randomUUID())
                .accountKind(kind)
                .currency(currency)
                .status(LedgerAccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
