package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.AccountServiceClient;
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
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

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
        // Given
        TransactionFilterRequest filter = TransactionFilterRequest.builder()
                .accountId("account-1")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .minAmount(BigDecimal.valueOf(50.00))
                .maxAmount(BigDecimal.valueOf(500.00))
                .build();

        Page<Transaction> mockPage = new PageImpl<>(Collections.singletonList(sampleTransaction));
        when(transactionRepository.findTransactionsWithFilters(
                eq("account-1"),
                eq(TransactionType.TRANSFER),
                eq(TransactionStatus.COMPLETED),
                any(),
                any(),
                eq(BigDecimal.valueOf(50.00)),
                eq(BigDecimal.valueOf(500.00)),
                any(),
                any(),
                eq(pageable)
        )).thenReturn(mockPage);

        // When
        Page<TransactionResponse> result = transactionService.searchTransactions(filter, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTransactionId()).isEqualTo("tx-123");
        assertThat(result.getContent().get(0).getAmount()).isEqualTo(BigDecimal.valueOf(100.00));
    }

    @Test
    void testGetAccountTransactionStats() {
        // Given
        String accountId = "account-1";
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();

        // Mock repository calls
        when(transactionRepository.countTransactionsByAccountAndDateRange(eq(accountId), any(), any()))
                .thenReturn(10L);
        when(transactionRepository.countTransactionsByAccountStatusAndDateRange(eq(accountId), eq(TransactionStatus.COMPLETED), any(), any()))
                .thenReturn(8L);
        when(transactionRepository.countTransactionsByAccountStatusAndDateRange(eq(accountId), eq(TransactionStatus.PROCESSING), any(), any()))
                .thenReturn(1L);
        when(transactionRepository.countTransactionsByAccountStatusAndDateRange(eq(accountId), eq(TransactionStatus.FAILED), any(), any()))
                .thenReturn(1L);
        when(transactionRepository.countTransactionsByAccountStatusAndDateRange(eq(accountId), eq(TransactionStatus.REVERSED), any(), any()))
                .thenReturn(0L);
        
        when(transactionRepository.sumTransactionAmountsByAccountAndDateRange(eq(accountId), any(), any()))
                .thenReturn(BigDecimal.valueOf(5000.00));
        when(transactionRepository.sumIncomingTransactionsByAccountAndDateRange(eq(accountId), any(), any()))
                .thenReturn(BigDecimal.valueOf(3000.00));
        when(transactionRepository.sumOutgoingTransactionsByAccountAndDateRange(eq(accountId), any(), any()))
                .thenReturn(BigDecimal.valueOf(2000.00));
        
        when(transactionRepository.sumTransactionAmountsByAccountTypeAndDateRange(eq(accountId), eq(TransactionType.DEPOSIT), any(), any()))
                .thenReturn(BigDecimal.valueOf(2000.00));
        when(transactionRepository.sumTransactionAmountsByAccountTypeAndDateRange(eq(accountId), eq(TransactionType.WITHDRAWAL), any(), any()))
                .thenReturn(BigDecimal.valueOf(1000.00));
        when(transactionRepository.sumTransactionAmountsByAccountTypeAndDateRange(eq(accountId), eq(TransactionType.TRANSFER), any(), any()))
                .thenReturn(BigDecimal.valueOf(2000.00));
        
        when(transactionRepository.countTransactionsByAccountTypeAndDateRange(eq(accountId), eq(TransactionType.DEPOSIT), any(), any()))
                .thenReturn(3L);
        when(transactionRepository.countTransactionsByAccountTypeAndDateRange(eq(accountId), eq(TransactionType.WITHDRAWAL), any(), any()))
                .thenReturn(2L);
        when(transactionRepository.countTransactionsByAccountTypeAndDateRange(eq(accountId), eq(TransactionType.TRANSFER), any(), any()))
                .thenReturn(5L);
        
        when(transactionRepository.findMaxTransactionAmount(eq(accountId), any(), any()))
                .thenReturn(BigDecimal.valueOf(1000.00));
        when(transactionRepository.findMinTransactionAmount(eq(accountId), any(), any()))
                .thenReturn(BigDecimal.valueOf(50.00));
        
        when(transactionRepository.getDailyTransactionSum(eq(accountId), eq(TransactionType.TRANSFER)))
                .thenReturn(BigDecimal.valueOf(500.00));
        when(transactionRepository.getMonthlyTransactionSum(eq(accountId), eq(TransactionType.TRANSFER)))
                .thenReturn(BigDecimal.valueOf(2000.00));
        when(transactionRepository.getDailyTransactionCount(eq(accountId), eq(TransactionType.TRANSFER)))
                .thenReturn(2L);
        when(transactionRepository.getMonthlyTransactionCount(eq(accountId), eq(TransactionType.TRANSFER)))
                .thenReturn(5L);

        // When
        TransactionStatsResponse result = transactionService.getAccountTransactionStats(accountId, startDate, endDate);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getTotalTransactions()).isEqualTo(10L);
        assertThat(result.getCompletedTransactions()).isEqualTo(8L);
        assertThat(result.getPendingTransactions()).isEqualTo(1L);
        assertThat(result.getFailedTransactions()).isEqualTo(1L);
        assertThat(result.getReversedTransactions()).isEqualTo(0L);
        assertThat(result.getTotalAmount()).isEqualTo(BigDecimal.valueOf(5000.00));
        assertThat(result.getTotalIncoming()).isEqualTo(BigDecimal.valueOf(3000.00));
        assertThat(result.getTotalOutgoing()).isEqualTo(BigDecimal.valueOf(2000.00));
        assertThat(result.getSuccessRate()).isEqualTo(80.0);
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getLargestTransaction()).isEqualTo(BigDecimal.valueOf(1000.00));
        assertThat(result.getSmallestTransaction()).isEqualTo(BigDecimal.valueOf(50.00));
        assertThat(result.getDailyTotal()).isEqualTo(BigDecimal.valueOf(500.00));
        assertThat(result.getMonthlyTotal()).isEqualTo(BigDecimal.valueOf(2000.00));
        assertThat(result.getDailyCount()).isEqualTo(2L);
        assertThat(result.getMonthlyCount()).isEqualTo(5L);
        
        // Check transaction counts by type
        assertThat(result.getTransactionCountsByType().get("DEPOSIT")).isEqualTo(3L);
        assertThat(result.getTransactionCountsByType().get("WITHDRAWAL")).isEqualTo(2L);
        assertThat(result.getTransactionCountsByType().get("TRANSFER")).isEqualTo(5L);
        
        // Check transaction amounts by type
        assertThat(result.getTransactionAmountsByType().get("DEPOSIT")).isEqualTo(BigDecimal.valueOf(2000.00));
        assertThat(result.getTransactionAmountsByType().get("WITHDRAWAL")).isEqualTo(BigDecimal.valueOf(1000.00));
        assertThat(result.getTransactionAmountsByType().get("TRANSFER")).isEqualTo(BigDecimal.valueOf(2000.00));
    }

    @Test
    void testGetAccountTransactionStatsWithDefaultDateRange() {
        // Given
        String accountId = "account-1";
        
        // Mock basic repository calls
        when(transactionRepository.countTransactionsByAccountAndDateRange(eq(accountId), any(), any()))
                .thenReturn(5L);
        when(transactionRepository.countTransactionsByAccountStatusAndDateRange(eq(accountId), any(), any(), any()))
                .thenReturn(0L);
        when(transactionRepository.sumTransactionAmountsByAccountAndDateRange(eq(accountId), any(), any()))
                .thenReturn(BigDecimal.valueOf(1000.00));
        when(transactionRepository.sumIncomingTransactionsByAccountAndDateRange(eq(accountId), any(), any()))
                .thenReturn(BigDecimal.valueOf(600.00));
        when(transactionRepository.sumOutgoingTransactionsByAccountAndDateRange(eq(accountId), any(), any()))
                .thenReturn(BigDecimal.valueOf(400.00));
        when(transactionRepository.sumTransactionAmountsByAccountTypeAndDateRange(eq(accountId), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.countTransactionsByAccountTypeAndDateRange(eq(accountId), any(), any(), any()))
                .thenReturn(0L);
        when(transactionRepository.findMaxTransactionAmount(eq(accountId), any(), any()))
                .thenReturn(BigDecimal.valueOf(500.00));
        when(transactionRepository.findMinTransactionAmount(eq(accountId), any(), any()))
                .thenReturn(BigDecimal.valueOf(100.00));
        when(transactionRepository.getDailyTransactionSum(eq(accountId), any()))
                .thenReturn(BigDecimal.valueOf(200.00));
        when(transactionRepository.getMonthlyTransactionSum(eq(accountId), any()))
                .thenReturn(BigDecimal.valueOf(1000.00));
        when(transactionRepository.getDailyTransactionCount(eq(accountId), any()))
                .thenReturn(1L);
        when(transactionRepository.getMonthlyTransactionCount(eq(accountId), any()))
                .thenReturn(5L);

        // When (passing null dates to test default behavior)
        TransactionStatsResponse result = transactionService.getAccountTransactionStats(accountId, null, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getPeriodStart()).isNotNull();
        assertThat(result.getPeriodEnd()).isNotNull();
        assertThat(result.getTotalTransactions()).isEqualTo(5L);
        assertThat(result.getTotalAmount()).isEqualTo(BigDecimal.valueOf(1000.00));
    }
}