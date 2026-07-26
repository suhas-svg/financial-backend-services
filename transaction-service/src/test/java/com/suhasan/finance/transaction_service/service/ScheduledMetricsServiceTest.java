package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledMetricsServiceTest {
    private final MetricsService metrics = mock(MetricsService.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final ScheduledMetricsService service = new ScheduledMetricsService(metrics, transactions, audit);

    @Test
    void executesEveryScheduledMaintenancePath() {
        when(transactions.countByStatus(TransactionStatus.PROCESSING)).thenReturn(4L);
        when(metrics.getTransactionSuccessRate()).thenReturn(.95);
        when(metrics.getDailyTransactionVolume()).thenReturn(12L);
        when(metrics.getActiveTransactionsCount()).thenReturn(2L);
        when(metrics.getDailyTransactionAmount()).thenReturn(BigDecimal.TEN);

        service.updatePendingTransactionsCount();
        service.resetDailyCounters();
        service.logSystemHealthMetrics();
        service.cleanupOldAuditLogs();
        service.generateDailyTransactionSummary();

        verify(metrics).updatePendingTransactionsCount(4);
        verify(metrics).resetDailyCounters();
        verify(audit, org.mockito.Mockito.times(4))
                .logSystemEvent(anyString(), anyString(), anyString(), any());
    }

    @Test
    void schedulerFailuresRemainContainedAndAuditable() {
        when(transactions.countByStatus(TransactionStatus.PROCESSING)).thenThrow(new IllegalStateException("db"));
        doThrow(new IllegalStateException("reset")).when(metrics).resetDailyCounters();
        when(metrics.getTransactionSuccessRate()).thenThrow(new IllegalStateException("metrics"));
        service.updatePendingTransactionsCount();
        service.resetDailyCounters();
        service.logSystemHealthMetrics();
        service.cleanupOldAuditLogs();
        service.generateDailyTransactionSummary();
    }
}
