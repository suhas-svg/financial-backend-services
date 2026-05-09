package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.RiskAlert;
import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import com.suhasan.finance.transaction_service.entity.RiskAlertType;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.RiskAlertRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskEvaluationServiceTest {

    private RiskAlertRepository riskAlertRepository;
    private TransactionRepository transactionRepository;
    private RiskEvaluationService riskEvaluationService;

    @BeforeEach
    void setUp() {
        riskAlertRepository = mock(RiskAlertRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        riskEvaluationService = new RiskEvaluationService(riskAlertRepository, transactionRepository);
        when(riskAlertRepository.save(any(RiskAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void completedHighValueTransferCreatesHighSeverityAlert() {
        Transaction transaction = transaction("txn-1", "user-1", TransactionType.TRANSFER,
                TransactionStatus.COMPLETED, new BigDecimal("6000.00"));
        when(riskAlertRepository.existsByDedupeKeyAndStatus("HIGH_VALUE_TRANSFER:txn-1", RiskAlertStatus.OPEN))
                .thenReturn(false);

        riskEvaluationService.evaluateCompletedTransaction(transaction);

        verify(riskAlertRepository).save(any(RiskAlert.class));
        verify(riskAlertRepository).save(org.mockito.ArgumentMatchers.argThat(alert ->
                alert.getAlertType() == RiskAlertType.HIGH_VALUE_TRANSFER
                        && alert.getSeverity() == RiskAlertSeverity.HIGH
                        && alert.getStatus() == RiskAlertStatus.OPEN
                        && "txn-1".equals(alert.getTransactionId())
                        && "user-1".equals(alert.getUserId())));
    }

    @Test
    void repeatedFailuresCreateMediumSeverityAlert() {
        Transaction transaction = transaction("txn-2", "user-1", TransactionType.TRANSFER,
                TransactionStatus.FAILED, new BigDecimal("25.00"));
        when(transactionRepository.countByCreatedByAndStatusInAndCreatedAtAfter(
                eq("user-1"), any(Collection.class), any(LocalDateTime.class))).thenReturn(3L);
        when(riskAlertRepository.existsByDedupeKeyAndStatus(any(), eq(RiskAlertStatus.OPEN))).thenReturn(false);

        riskEvaluationService.evaluateFailedTransaction(transaction);

        verify(riskAlertRepository).save(org.mockito.ArgumentMatchers.argThat(alert ->
                alert.getAlertType() == RiskAlertType.REPEATED_FAILURES
                        && alert.getSeverity() == RiskAlertSeverity.MEDIUM
                        && alert.getReason().contains("3 failed transactions")));
    }

    @Test
    void rapidCompletedTransfersCreateMediumSeverityAlert() {
        Transaction transaction = transaction("txn-3", "user-1", TransactionType.TRANSFER,
                TransactionStatus.COMPLETED, new BigDecimal("100.00"));
        when(transactionRepository.countByCreatedByAndTypeAndStatusAndCreatedAtAfter(
                eq("user-1"), eq(TransactionType.TRANSFER), eq(TransactionStatus.COMPLETED), any(LocalDateTime.class)))
                .thenReturn(5L);
        when(riskAlertRepository.existsByDedupeKeyAndStatus(any(), eq(RiskAlertStatus.OPEN))).thenReturn(false);

        riskEvaluationService.evaluateCompletedTransaction(transaction);

        verify(riskAlertRepository).save(org.mockito.ArgumentMatchers.argThat(alert ->
                alert.getAlertType() == RiskAlertType.RAPID_TRANSFERS
                        && alert.getSeverity() == RiskAlertSeverity.MEDIUM
                        && alert.getReason().contains("5 completed transfers")));
    }

    @Test
    void reversalHeavyActivityCreatesHighSeverityAlert() {
        Transaction transaction = transaction("txn-4", "user-1", TransactionType.REVERSAL,
                TransactionStatus.COMPLETED, new BigDecimal("100.00"));
        when(transactionRepository.countByCreatedByAndTypeAndStatusAndCreatedAtAfter(
                eq("user-1"), eq(TransactionType.REVERSAL), eq(TransactionStatus.COMPLETED), any(LocalDateTime.class)))
                .thenReturn(2L);
        when(riskAlertRepository.existsByDedupeKeyAndStatus(any(), eq(RiskAlertStatus.OPEN))).thenReturn(false);

        riskEvaluationService.evaluateReversalTransaction(transaction);

        verify(riskAlertRepository).save(org.mockito.ArgumentMatchers.argThat(alert ->
                alert.getAlertType() == RiskAlertType.REVERSAL_HEAVY_ACTIVITY
                        && alert.getSeverity() == RiskAlertSeverity.HIGH
                        && alert.getReason().contains("2 reversals")));
    }

    @Test
    void dedupePreventsDuplicateOpenAlerts() {
        Transaction transaction = transaction("txn-1", "user-1", TransactionType.TRANSFER,
                TransactionStatus.COMPLETED, new BigDecimal("6000.00"));
        when(riskAlertRepository.existsByDedupeKeyAndStatus("HIGH_VALUE_TRANSFER:txn-1", RiskAlertStatus.OPEN))
                .thenReturn(true);

        riskEvaluationService.evaluateCompletedTransaction(transaction);

        verify(riskAlertRepository, never()).save(any(RiskAlert.class));
    }

    @Test
    void nonRiskyTransactionCreatesNoAlert() {
        Transaction transaction = transaction("txn-5", "user-1", TransactionType.TRANSFER,
                TransactionStatus.COMPLETED, new BigDecimal("100.00"));
        when(transactionRepository.countByCreatedByAndTypeAndStatusAndCreatedAtAfter(
                eq("user-1"), eq(TransactionType.TRANSFER), eq(TransactionStatus.COMPLETED), any(LocalDateTime.class)))
                .thenReturn(1L);

        riskEvaluationService.evaluateCompletedTransaction(transaction);

        verify(riskAlertRepository, never()).save(any(RiskAlert.class));
        assertThat(transaction.getAmount()).isEqualByComparingTo("100.00");
    }

    private Transaction transaction(String transactionId, String userId, TransactionType type,
            TransactionStatus status, BigDecimal amount) {
        return Transaction.builder()
                .transactionId(transactionId)
                .createdBy(userId)
                .fromAccountId("acc-1")
                .toAccountId("acc-2")
                .type(type)
                .status(status)
                .amount(amount)
                .currency("USD")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
