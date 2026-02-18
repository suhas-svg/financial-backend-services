package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.TransactionFilterRequest;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransactionStatsResponse;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class TransactionHistoryServiceTest {

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

    private Transaction sampleTransaction;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        sampleTransaction = Transaction.builder()
                .transactionId("tx-123")
                .fromAccountId("account-1")
                .toAccountId("account-2")
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .description("Test transaction")
                .createdAt(LocalDateTime.now())
                .createdBy("test-user")
                .build();

        pageable = PageRequest.of(0, 20);
    }

    @Test
    void testSearchTransactions() {
        TransactionFilterRequest filter = TransactionFilterRequest.builder()
                .accountId("account-1")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .minAmount(BigDecimal.valueOf(50.00))
                .maxAmount(BigDecimal.valueOf(500.00))
                .createdBy("test-user")
                .build();

        Page<Transaction> scoped = new PageImpl<>(List.of(sampleTransaction));
        when(transactionRepository.findByCreatedByOrderByCreatedAtDesc(eq("test-user"), eq(Pageable.unpaged())))
                .thenReturn(scoped);

        Page<TransactionResponse> result = transactionService.searchTransactions(filter, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTransactionId()).isEqualTo("tx-123");
        assertThat(result.getContent().get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
    }

    @Test
    void testGetAccountTransactionStats() {
        String accountId = "account-1";
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now().plusDays(1);

        Transaction completedTransfer = Transaction.builder()
                .transactionId("t1")
                .fromAccountId("account-1")
                .toAccountId("account-2")
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .createdAt(LocalDateTime.now().minusDays(1))
                .createdBy("test-user")
                .build();

        Transaction completedDeposit = Transaction.builder()
                .transactionId("t2")
                .fromAccountId("EXTERNAL")
                .toAccountId("account-1")
                .amount(BigDecimal.valueOf(40.00))
                .currency("USD")
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .createdAt(LocalDateTime.now().minusDays(1))
                .createdBy("test-user")
                .build();

        Transaction processingTransfer = Transaction.builder()
                .transactionId("t3")
                .fromAccountId("account-1")
                .toAccountId("account-3")
                .amount(BigDecimal.valueOf(20.00))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PROCESSING)
                .createdAt(LocalDateTime.now().minusHours(2))
                .createdBy("test-user")
                .build();

        Transaction failedWithdrawal = Transaction.builder()
                .transactionId("t4")
                .fromAccountId("account-1")
                .toAccountId("EXTERNAL")
                .amount(BigDecimal.valueOf(30.00))
                .currency("USD")
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.FAILED)
                .createdAt(LocalDateTime.now().minusHours(1))
                .createdBy("test-user")
                .build();

        Transaction reversedTransfer = Transaction.builder()
                .transactionId("t5")
                .fromAccountId("account-1")
                .toAccountId("account-4")
                .amount(BigDecimal.valueOf(10.00))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.REVERSED)
                .createdAt(LocalDateTime.now().minusMinutes(30))
                .createdBy("test-user")
                .build();

        when(transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                eq(accountId), eq(accountId), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(
                        completedTransfer,
                        completedDeposit,
                        processingTransfer,
                        failedWithdrawal,
                        reversedTransfer
                )));

        TransactionStatsResponse result = transactionService.getAccountTransactionStats(accountId, startDate, endDate);

        assertThat(result).isNotNull();
        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getTotalTransactions()).isEqualTo(5L);
        assertThat(result.getCompletedTransactions()).isEqualTo(2L);
        assertThat(result.getPendingTransactions()).isEqualTo(1L);
        assertThat(result.getFailedTransactions()).isEqualTo(1L);
        assertThat(result.getReversedTransactions()).isEqualTo(1L);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(140.00));
        assertThat(result.getTotalIncoming()).isEqualByComparingTo(BigDecimal.valueOf(40.00));
        assertThat(result.getTotalOutgoing()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(result.getLargestTransaction()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(result.getSmallestTransaction()).isEqualByComparingTo(BigDecimal.valueOf(40.00));
        assertThat(result.getSuccessRate()).isEqualTo(40.0);
    }

    @Test
    void testGetAccountTransactionStatsWithDefaultDateRange() {
        String accountId = "account-1";
        Transaction tx = Transaction.builder()
                .transactionId("t-default")
                .fromAccountId("account-1")
                .toAccountId("account-2")
                .amount(BigDecimal.valueOf(250.00))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .createdAt(LocalDateTime.now().minusDays(1))
                .createdBy("test-user")
                .build();

        when(transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                eq(accountId), eq(accountId), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(tx)));

        TransactionStatsResponse result = transactionService.getAccountTransactionStats(accountId, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getPeriodStart()).isNotNull();
        assertThat(result.getPeriodEnd()).isNotNull();
        assertThat(result.getTotalTransactions()).isEqualTo(1L);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(250.00));
    }
}
