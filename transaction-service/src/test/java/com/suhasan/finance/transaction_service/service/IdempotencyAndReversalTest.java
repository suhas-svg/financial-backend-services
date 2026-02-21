package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.TransactionFilterRequest;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.exception.TransactionAlreadyReversedException;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IdempotencyAndReversalTest — targeted unit tests for all C1, C1b, and M2
 * fixes.
 *
 * <p>
 * Covers:
 * <ul>
 * <li>C1: Transfer idempotency via DB unique constraint (DIVE catch →
 * re-query)</li>
 * <li>C1: Deposit idempotency key pre-check and DIVE fallback</li>
 * <li>C1b: reverseTransaction uses findByIdWithLock (SELECT FOR UPDATE)</li>
 * <li>C1b: Concurrent reversal DIVE → TransactionAlreadyReversedException</li>
 * <li>M2: searchTransactions delegates to findTransactionsWithFilters (no
 * findAll)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Idempotency and Reversal Fix Tests (C1 / C1b / M2)")
@SuppressWarnings("null")
class IdempotencyAndReversalTest {

        @Mock
        private TransactionRepository transactionRepository;

        @Mock
        private ResilientAccountServiceClient accountServiceClient;

        @Mock
        private AuditService auditService;

        @Mock
        private MetricsService metricsService;

        @Mock
        private TransactionLimitService transactionLimitService;

        @InjectMocks
        private TransactionServiceImpl transactionService;

        private static final String USER_ID = "user-test-001";
        private static final String ACCOUNT_A = "acc-a";
        private static final String ACCOUNT_B = "acc-b";
        private static final String IDEMPOTENCY_KEY = "IDEM-TRANSFER-ABC";

        @BeforeEach
        void setUp() {
                when(transactionLimitService.validateTransactionLimits(anyString(), anyString(),
                                any(TransactionType.class), any(BigDecimal.class))).thenReturn(true);
        }

        // ─────────────────────────────────────────────────────────────────────────
        // C1 — Transfer idempotency
        // ─────────────────────────────────────────────────────────────────────────

        @Nested
        @DisplayName("C1 — Transfer: DataIntegrityViolationException → idempotent reply")
        class TransferIdempotency {

                @Test
                @DisplayName("Pre-check finds existing tx → returns immediately without DB write")
                void transfer_ExistingIdempotentTx_ReturnedWithoutSave() {
                        var existingTx = completedTransfer(IDEMPOTENCY_KEY);
                        when(transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(
                                        USER_ID, TransactionType.TRANSFER, IDEMPOTENCY_KEY))
                                        .thenReturn(Optional.of(existingTx));

                        var result = transactionService.processTransfer(
                                        transferRequest(ACCOUNT_A, ACCOUNT_B, BigDecimal.valueOf(100)),
                                        USER_ID, IDEMPOTENCY_KEY);

                        assertThat(result.getTransactionId()).isEqualTo(existingTx.getTransactionId());
                        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
                        // Must NEVER hit the account service for an idempotent replay
                        verify(accountServiceClient, never()).applyBalanceOperation(
                                        anyString(), anyString(), any(), anyString(), anyString(), anyBoolean());
                        verify(transactionRepository, never()).save(any());
                }

                @Test
                @DisplayName("DIVE on initial PROCESSING save → caught at line 102, re-queries and returns existing COMPLETED tx")
                void transfer_DataIntegrityViolation_ReturnsExistingTransaction() {
                        var fromAccount = accountDto(ACCOUNT_A, BigDecimal.valueOf(1000));
                        var savedTx = completedTransfer(IDEMPOTENCY_KEY);

                        when(accountServiceClient.getAccount(ACCOUNT_A)).thenReturn(fromAccount);
                        when(accountServiceClient.getAccount(ACCOUNT_B))
                                        .thenReturn(accountDto(ACCOUNT_B, BigDecimal.valueOf(500)));

                        // Pre-check: key not found yet (race window); post-DIVE re-query returns winner
                        when(transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(
                                        USER_ID, TransactionType.TRANSFER, IDEMPOTENCY_KEY))
                                        .thenReturn(Optional.empty())
                                        .thenReturn(Optional.of(savedTx));

                        // C1 fix: DIVE thrown on the very first PROCESSING save — service catches it
                        // and returns the winner's row. No debit/credit calls ever happen.
                        when(transactionRepository.save(any(Transaction.class)))
                                        .thenAnswer(inv -> {
                                                Transaction t = inv.getArgument(0);
                                                if (t.getStatus() == TransactionStatus.PROCESSING) {
                                                        throw new DataIntegrityViolationException(
                                                                        "duplicate key value violates unique constraint uk_transaction_idempotency_key");
                                                }
                                                return t;
                                        });

                        var result = transactionService.processTransfer(
                                        transferRequest(ACCOUNT_A, ACCOUNT_B, BigDecimal.valueOf(100)),
                                        USER_ID, IDEMPOTENCY_KEY);

                        assertThat(result).isNotNull();
                        assertThat(result.getTransactionId()).isEqualTo(savedTx.getTransactionId());
                        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

                        // idempotency lookup called at least twice: once pre-save, once post-DIVE
                        verify(transactionRepository, atLeast(2))
                                        .findFirstByCreatedByAndTypeAndIdempotencyKey(
                                                        USER_ID, TransactionType.TRANSFER, IDEMPOTENCY_KEY);
                        // Account service must NOT have been called — DIVE fired before any balance ops
                        verify(accountServiceClient, never()).applyBalanceOperation(
                                        anyString(), anyString(), any(), anyString(), anyString(), anyBoolean());
                }

                @Test
                @DisplayName("Null idempotency key → no pre-check, proceeds to fresh processing")
                void transfer_NullIdempotencyKey_Proceeds() {
                        when(accountServiceClient.getAccount(ACCOUNT_A))
                                        .thenReturn(accountDto(ACCOUNT_A, BigDecimal.valueOf(1000)));
                        when(accountServiceClient.getAccount(ACCOUNT_B))
                                        .thenReturn(accountDto(ACCOUNT_B, BigDecimal.valueOf(500)));
                        var savedTx = completedTransfer(null);
                        when(transactionRepository.save(any())).thenReturn(savedTx);
                        when(accountServiceClient.applyBalanceOperation(eq(ACCOUNT_A), anyString(),
                                        eq(BigDecimal.valueOf(-100)), anyString(), eq("TRANSFER_DEBIT"), eq(false)))
                                        .thenReturn(balanceOpResponse(ACCOUNT_A));
                        when(accountServiceClient.applyBalanceOperation(eq(ACCOUNT_B), anyString(),
                                        eq(BigDecimal.valueOf(100)), anyString(), eq("TRANSFER_CREDIT"), eq(true)))
                                        .thenReturn(balanceOpResponse(ACCOUNT_B));

                        var result = transactionService.processTransfer(
                                        transferRequest(ACCOUNT_A, ACCOUNT_B, BigDecimal.valueOf(100)),
                                        USER_ID, null);

                        assertThat(result).isNotNull();
                }
        }

        // ─────────────────────────────────────────────────────────────────────────
        // C1 — Deposit idempotency
        // ─────────────────────────────────────────────────────────────────────────

        @Nested
        @DisplayName("C1 — Deposit: idempotency and DIVE handling")
        class DepositIdempotency {

                @Test
                @DisplayName("Pre-check finds existing deposit → returns immediately, no account call")
                void deposit_ExistingKey_ReturnedImmediately() {
                        var existingTx = Transaction.builder()
                                        .transactionId("dep-existing-001")
                                        .type(TransactionType.DEPOSIT)
                                        .status(TransactionStatus.COMPLETED)
                                        .amount(BigDecimal.valueOf(200))
                                        .currency("USD")
                                        .toAccountId(ACCOUNT_A)
                                        .createdBy(USER_ID)
                                        .idempotencyKey("DEP-IDEM-001")
                                        .build();

                        when(transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(
                                        USER_ID, TransactionType.DEPOSIT, "DEP-IDEM-001"))
                                        .thenReturn(Optional.of(existingTx));

                        var result = transactionService.processDeposit(ACCOUNT_A, BigDecimal.valueOf(200),
                                        "Test deposit", USER_ID, "DEP-IDEM-001");

                        assertThat(result.getTransactionId()).isEqualTo("dep-existing-001");
                        verify(accountServiceClient, never()).applyBalanceOperation(
                                        anyString(), anyString(), any(), anyString(), anyString(), anyBoolean());
                }

                @Test
                @DisplayName("DIVE on deposit save → re-queries and returns idempotent response")
                void deposit_DataIntegrityViolation_ReturnsExistingTransaction() {
                        var existingTx = Transaction.builder()
                                        .transactionId("dep-concurrent-001")
                                        .type(TransactionType.DEPOSIT)
                                        .status(TransactionStatus.COMPLETED)
                                        .amount(BigDecimal.valueOf(200))
                                        .currency("USD")
                                        .toAccountId(ACCOUNT_A)
                                        .createdBy(USER_ID)
                                        .idempotencyKey("DEP-RACE-001")
                                        .build();

                        when(accountServiceClient.getAccount(ACCOUNT_A))
                                        .thenReturn(accountDto(ACCOUNT_A, BigDecimal.valueOf(1000)));
                        when(transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(
                                        USER_ID, TransactionType.DEPOSIT, "DEP-RACE-001"))
                                        .thenReturn(Optional.empty())
                                        .thenReturn(Optional.of(existingTx));
                        // C1 fix: DIVE on first PROCESSING save — caught at line 225, no balance ops
                        // called
                        when(transactionRepository.save(any())).thenAnswer(inv -> {
                                Transaction t = inv.getArgument(0);
                                if (t.getStatus() == TransactionStatus.PROCESSING) {
                                        throw new DataIntegrityViolationException(
                                                        "uk_transaction_idempotency_key violation");
                                }
                                return t;
                        });

                        var result = transactionService.processDeposit(ACCOUNT_A, BigDecimal.valueOf(200),
                                        "Test deposit", USER_ID, "DEP-RACE-001");

                        assertThat(result.getTransactionId()).isEqualTo("dep-concurrent-001");
                }
        }

        // ─────────────────────────────────────────────────────────────────────────
        // C1b — Reversal race condition fix
        // ─────────────────────────────────────────────────────────────────────────

        @Nested
        @DisplayName("C1b — Reversal: SELECT FOR UPDATE + DIVE → TransactionAlreadyReversedException")
        class ReversalRaceFix {

                private Transaction originalCompletedTx;

                @BeforeEach
                void setUp() {
                        originalCompletedTx = Transaction.builder()
                                        .transactionId("orig-tx-001")
                                        .fromAccountId(ACCOUNT_A)
                                        .toAccountId(ACCOUNT_B)
                                        .amount(BigDecimal.valueOf(100))
                                        .currency("USD")
                                        .type(TransactionType.TRANSFER)
                                        .status(TransactionStatus.COMPLETED)
                                        .createdAt(LocalDateTime.now().minusHours(2))
                                        .createdBy(USER_ID)
                                        .build();
                }

                @Test
                @DisplayName("Reversal uses findByIdWithLock (SELECT FOR UPDATE), never plain findById")
                void reversal_UsesPessimisticLock() {
                        when(transactionRepository.findByIdWithLock("orig-tx-001"))
                                        .thenReturn(Optional.of(originalCompletedTx));
                        when(transactionRepository.isTransactionReversed("orig-tx-001")).thenReturn(false);
                        when(transactionRepository.save(any())).thenAnswer(inv -> {
                                Transaction t = inv.getArgument(0);
                                if (t.getTransactionId() == null)
                                        t.setTransactionId("rev-001");
                                return t;
                        });
                        when(accountServiceClient.applyBalanceOperation(eq(ACCOUNT_B), anyString(),
                                        eq(BigDecimal.valueOf(-100)), anyString(), eq("REVERSAL_DEBIT"), eq(false)))
                                        .thenReturn(balanceOpResponse(ACCOUNT_B));
                        when(accountServiceClient.applyBalanceOperation(eq(ACCOUNT_A), anyString(),
                                        eq(BigDecimal.valueOf(100)), anyString(), eq("REVERSAL_CREDIT"), eq(true)))
                                        .thenReturn(balanceOpResponse(ACCOUNT_A));

                        var result = transactionService.reverseTransaction(
                                        "orig-tx-001", "Test", USER_ID, "REV-IDEM-001");

                        assertThat(result).isNotNull();
                        assertThat(result.getType()).isEqualTo(TransactionType.REVERSAL);

                        // The pessimistic locking overload MUST have been called
                        verify(transactionRepository).findByIdWithLock("orig-tx-001");
                        // Plain findById must NOT be used for this flow
                        verify(transactionRepository, never()).findById("orig-tx-001");
                }

                @Test
                @DisplayName("DIVE on reversal INSERT → TransactionAlreadyReversedException")
                void reversal_DataIntegrityViolation_ThrowsAlreadyReversed() {
                        when(transactionRepository.findByIdWithLock("orig-tx-001"))
                                        .thenReturn(Optional.of(originalCompletedTx));
                        when(transactionRepository.isTransactionReversed("orig-tx-001")).thenReturn(false);
                        when(accountServiceClient.applyBalanceOperation(eq(ACCOUNT_B), anyString(),
                                        eq(BigDecimal.valueOf(-100)), anyString(), eq("REVERSAL_DEBIT"), eq(false)))
                                        .thenReturn(balanceOpResponse(ACCOUNT_B));
                        when(accountServiceClient.applyBalanceOperation(eq(ACCOUNT_A), anyString(),
                                        eq(BigDecimal.valueOf(100)), anyString(), eq("REVERSAL_CREDIT"), eq(true)))
                                        .thenReturn(balanceOpResponse(ACCOUNT_A));

                        // Simulate: concurrent reversal won the race — partial unique index raises DIVE
                        when(transactionRepository.save(any())).thenAnswer(inv -> {
                                Transaction t = inv.getArgument(0);
                                if (t.getType() == TransactionType.REVERSAL
                                                && t.getStatus() == TransactionStatus.PROCESSING) {
                                        throw new DataIntegrityViolationException(
                                                        "duplicate key value violates unique constraint uk_reversal_per_original_transaction");
                                }
                                return t;
                        });

                        assertThatThrownBy(() -> transactionService.reverseTransaction("orig-tx-001", "race", USER_ID,
                                        "REV-IDEM-002"))
                                        .isInstanceOf(TransactionAlreadyReversedException.class)
                                        .hasMessageContaining("orig-tx-001");
                }

                @Test
                @DisplayName("isTransactionReversed=true blocks reversal before any account call")
                void reversal_AlreadyReversedInDb_ThrowsImmediately() {
                        when(transactionRepository.findByIdWithLock("orig-tx-001"))
                                        .thenReturn(Optional.of(originalCompletedTx));
                        when(transactionRepository.isTransactionReversed("orig-tx-001")).thenReturn(true);

                        assertThatThrownBy(() -> transactionService.reverseTransaction("orig-tx-001", "duplicate",
                                        USER_ID, null))
                                        .isInstanceOf(TransactionAlreadyReversedException.class);

                        verify(accountServiceClient, never()).applyBalanceOperation(
                                        anyString(), anyString(), any(), anyString(), anyString(), anyBoolean());
                }

                @Test
                @DisplayName("Transaction not found via lock → IllegalArgumentException")
                void reversal_NotFound_ThrowsIllegalArgument() {
                        when(transactionRepository.findByIdWithLock("nonexistent"))
                                        .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> transactionService.reverseTransaction("nonexistent", "reason", USER_ID,
                                        null))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("Transaction not found");
                }

                @Test
                @DisplayName("Cannot reverse a REVERSAL transaction type")
                void reversal_CannotReverseReversal() {
                        var reversalTx = Transaction.builder()
                                        .transactionId("rev-tx-001")
                                        .type(TransactionType.REVERSAL)
                                        .status(TransactionStatus.COMPLETED)
                                        .createdAt(LocalDateTime.now().minusHours(1))
                                        .createdBy(USER_ID)
                                        .build();
                        when(transactionRepository.findByIdWithLock("rev-tx-001"))
                                        .thenReturn(Optional.of(reversalTx));

                        assertThatThrownBy(() -> transactionService.reverseTransaction("rev-tx-001", "error", USER_ID,
                                        null))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("Cannot reverse a reversal");
                }

                @Test
                @DisplayName("Transaction older than 30 days cannot be reversed")
                void reversal_TooOld_ThrowsIllegalArgument() {
                        var oldTx = Transaction.builder()
                                        .transactionId("old-tx-001")
                                        .type(TransactionType.TRANSFER)
                                        .status(TransactionStatus.COMPLETED)
                                        .createdAt(LocalDateTime.now().minusDays(35))
                                        .createdBy(USER_ID)
                                        .build();
                        when(transactionRepository.findByIdWithLock("old-tx-001"))
                                        .thenReturn(Optional.of(oldTx));

                        assertThatThrownBy(() -> transactionService.reverseTransaction("old-tx-001", "too old", USER_ID,
                                        null))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("30 days");
                }

                @Test
                @DisplayName("Idempotent reversal pre-check: same reversal idempotency key → returns existing")
                void reversal_ExistingIdempotencyKey_ReturnedWithoutDuplicate() {
                        var existingReversal = Transaction.builder()
                                        .transactionId("rev-existing-001")
                                        .type(TransactionType.REVERSAL)
                                        .status(TransactionStatus.COMPLETED)
                                        .originalTransactionId("orig-tx-001")
                                        .amount(BigDecimal.valueOf(100))
                                        .currency("USD")
                                        .createdBy(USER_ID)
                                        .idempotencyKey("REV-IDEM-EXISTING")
                                        .build();

                        when(transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(
                                        USER_ID, TransactionType.REVERSAL, "REV-IDEM-EXISTING"))
                                        .thenReturn(Optional.of(existingReversal));

                        var result = transactionService.reverseTransaction(
                                        "orig-tx-001", "retry", USER_ID, "REV-IDEM-EXISTING");

                        assertThat(result.getTransactionId()).isEqualTo("rev-existing-001");
                        // Never hit the locked read or account service on idempotent replay
                        verify(transactionRepository, never()).findByIdWithLock(anyString());
                        verify(accountServiceClient, never()).applyBalanceOperation(
                                        anyString(), anyString(), any(), anyString(), anyString(), anyBoolean());
                }
        }

        // ─────────────────────────────────────────────────────────────────────────
        // M2 — searchTransactions delegates to DB query
        // ─────────────────────────────────────────────────────────────────────────

        @Nested
        @DisplayName("M2 — searchTransactions uses DB-level JPQL, not in-memory findAll")
        class SearchTransactionsDatabaseOptimization {

                @Test
                @DisplayName("searchTransactions calls findTransactionsWithFilters, never findAll()")
                void searchTransactions_UsesDatabaseQuery_NotFindAll() {
                        var pageable = PageRequest.of(0, 20);
                        when(transactionRepository.findTransactionsWithFilters(
                                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                                        .thenReturn(Page.empty(pageable));

                        var filter = TransactionFilterRequest.builder()
                                        .accountId(ACCOUNT_A)
                                        .build();

                        transactionService.searchTransactions(filter, pageable);

                        // Verify DB delegation happened
                        verify(transactionRepository).findTransactionsWithFilters(
                                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

                        // The old unbounded findAll must NEVER be called
                        verify(transactionRepository, never()).findAll();
                }

                @Test
                @DisplayName("Empty filter still delegates to DB (no in-memory filtering)")
                void searchTransactions_EmptyFilter_StillUsesDatabaseQuery() {
                        var pageable = PageRequest.of(0, 10);
                        when(transactionRepository.findTransactionsWithFilters(
                                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                                        .thenReturn(Page.empty(pageable));

                        var emptyFilter = new TransactionFilterRequest();
                        transactionService.searchTransactions(emptyFilter, pageable);

                        verify(transactionRepository).findTransactionsWithFilters(
                                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
                }
        }

        // ─────────────────────────────────────────────────────────────────────────
        // Helpers
        // ─────────────────────────────────────────────────────────────────────────

        private com.suhasan.finance.transaction_service.dto.AccountDto accountDto(
                        String id, BigDecimal balance) {
                return com.suhasan.finance.transaction_service.dto.AccountDto.builder()
                                .id((long) id.hashCode())
                                .balance(balance)
                                .accountType("CHECKING")
                                .availableCredit(BigDecimal.ZERO)
                                .build();
        }

        private TransferRequest transferRequest(String from, String to, BigDecimal amount) {
                return TransferRequest.builder()
                                .fromAccountId(from)
                                .toAccountId(to)
                                .amount(amount)
                                .currency("USD")
                                .description("Test transfer")
                                .reference("REF-001")
                                .build();
        }

        private Transaction completedTransfer(String idemKey) {
                return Transaction.builder()
                                .transactionId("tx-" + (idemKey != null ? idemKey : "null"))
                                .fromAccountId(ACCOUNT_A)
                                .toAccountId(ACCOUNT_B)
                                .amount(BigDecimal.valueOf(100))
                                .currency("USD")
                                .type(TransactionType.TRANSFER)
                                .status(TransactionStatus.COMPLETED)
                                .createdBy(USER_ID)
                                .idempotencyKey(idemKey)
                                .createdAt(LocalDateTime.now().minusMinutes(5))
                                .build();
        }

        private ResilientAccountServiceClient.BalanceOperationResponse balanceOpResponse(String accountId) {
                return new ResilientAccountServiceClient.BalanceOperationResponse(
                                (long) accountId.hashCode(), "op-" + accountId, true,
                                BigDecimal.valueOf(900), 1L, "APPLIED");
        }
}
