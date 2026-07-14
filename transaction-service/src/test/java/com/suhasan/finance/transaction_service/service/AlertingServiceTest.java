package com.suhasan.finance.transaction_service.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertingServiceTest {
    @Mock MetricsService metricsService;
    @Mock AuditService auditService;

    private SimpleMeterRegistry meterRegistry;
    private AlertingService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new AlertingService(meterRegistry, metricsService, auditService);
        ReflectionTestUtils.setField(service, "errorRateThreshold", 0.05d);
        ReflectionTestUtils.setField(service, "responseTimeThreshold", 2_000L);
        ReflectionTestUtils.setField(service, "accountServiceErrorThreshold", 4);
        ReflectionTestUtils.setField(service, "dailyVolumeThreshold", 10_000L);
        ReflectionTestUtils.setField(service, "schedulingEnabled", true);
    }

    @Test
    void highErrorRateTriggersCriticalAlertAfterThreeChecksAndSuppressesDuplicate() {
        when(metricsService.getTransactionSuccessRate()).thenReturn(0.90d);
        when(metricsService.getDailyTransactionVolume()).thenReturn(100L);
        when(metricsService.getActiveTransactionsCount()).thenReturn(2L);

        service.checkCriticalTransactionFailures();
        service.checkCriticalTransactionFailures();
        service.checkCriticalTransactionFailures();
        service.checkCriticalTransactionFailures();

        verify(auditService, times(1)).logSystemEvent(
                eq("ALERT_TRIGGERED"), eq("AlertingService"), anyString(),
                org.mockito.ArgumentMatchers.<Map<String, String>>argThat(
                        details -> "HIGH_ERROR_RATE".equals(details.get("alertType"))
                                && "CRITICAL".equals(details.get("alertLevel"))));
        assertThat(service.getAlertStatistics())
                .containsEntry("criticalAlerts", 1.0d)
                .containsEntry("consecutiveHighErrorRateMinutes", 4L)
                .containsEntry("activeAlertSuppressions", 1);
    }

    @Test
    void normalErrorRateResetsConsecutiveCounter() {
        when(metricsService.getTransactionSuccessRate()).thenReturn(0.90d, 0.99d);
        when(metricsService.getDailyTransactionVolume()).thenReturn(0L);
        when(metricsService.getActiveTransactionsCount()).thenReturn(0L);

        service.checkCriticalTransactionFailures();
        service.checkCriticalTransactionFailures();

        assertThat(service.getAlertStatistics()).containsEntry("consecutiveHighErrorRateMinutes", 0L);
        verify(auditService, never()).logSystemEvent(any(), any(), any(), any());
    }

    @Test
    void volumeAndActiveTransactionThresholdsProduceDistinctWarnings() {
        when(metricsService.getTransactionSuccessRate()).thenReturn(1.0d);
        when(metricsService.getDailyTransactionVolume()).thenReturn(10_001L);
        when(metricsService.getActiveTransactionsCount()).thenReturn(101L);

        service.checkCriticalTransactionFailures();

        ArgumentCaptor<Map<String, String>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditService, times(2)).logSystemEvent(
                eq("ALERT_TRIGGERED"), eq("AlertingService"), anyString(), details.capture());
        assertThat(details.getAllValues()).extracting(map -> map.get("alertType"))
                .containsExactlyInAnyOrder("HIGH_DAILY_VOLUME", "HIGH_ACTIVE_TRANSACTIONS");
        assertThat(service.getAlertStatistics()).containsEntry("warningAlerts", 2.0d);
    }

    @Test
    void accountServiceErrorsEscalateAndRecoveryCreatesInfoAlert() {
        service.recordAccountServiceError();
        service.recordAccountServiceError();
        service.recordAccountServiceError();
        service.recordAccountServiceError();
        service.resetAccountServiceErrorCount();

        ArgumentCaptor<Map<String, String>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditService, times(3)).logSystemEvent(
                eq("ALERT_TRIGGERED"), eq("AlertingService"), anyString(), details.capture());
        assertThat(details.getAllValues()).extracting(map -> map.get("alertType"))
                .containsExactlyInAnyOrder(
                        "ACCOUNT_SERVICE_DEGRADED",
                        "ACCOUNT_SERVICE_UNAVAILABLE",
                        "ACCOUNT_SERVICE_RECOVERED");
        assertThat(service.getAlertStatistics())
                .containsEntry("consecutiveAccountServiceErrors", 0L)
                .containsEntry("criticalAlerts", 1.0d)
                .containsEntry("warningAlerts", 1.0d)
                .containsEntry("infoAlerts", 1.0d);
    }

    @Test
    void fiveSlowTransactionsTriggerOneWarningAndFastResponseResetsState() {
        for (int i = 0; i < 6; i++) {
            service.recordSlowTransaction(2_500L, "TRANSFER");
        }

        verify(auditService, times(1)).logSystemEvent(
                eq("ALERT_TRIGGERED"), eq("AlertingService"), anyString(),
                org.mockito.ArgumentMatchers.<Map<String, String>>argThat(
                        details -> "SLOW_TRANSACTION_PROCESSING".equals(details.get("alertType"))));
        assertThat(service.getAlertStatistics()).containsEntry("consecutiveSlowResponseMinutes", 6L);

        service.recordSlowTransaction(500L, "TRANSFER");
        assertThat(service.getAlertStatistics()).containsEntry("consecutiveSlowResponseMinutes", 0L);
    }

    @Test
    void disabledSchedulingSkipsMetricsAndMetricFailureRaisesCriticalAlert() {
        ReflectionTestUtils.setField(service, "schedulingEnabled", false);
        service.checkCriticalTransactionFailures();
        service.checkAccountServiceHealth();
        verify(metricsService, never()).getTransactionSuccessRate();

        ReflectionTestUtils.setField(service, "schedulingEnabled", true);
        when(metricsService.getTransactionSuccessRate()).thenThrow(new IllegalStateException("registry unavailable"));
        service.checkCriticalTransactionFailures();

        verify(auditService).logSystemEvent(
                eq("ALERT_TRIGGERED"), eq("AlertingService"), anyString(),
                org.mockito.ArgumentMatchers.<Map<String, String>>argThat(
                        details -> "ALERTING_SYSTEM_FAILURE".equals(details.get("alertType"))));
    }

    @Test
    void clearSuppressionAllowsAlertToBeEmittedAgain() {
        for (int i = 0; i < 4; i++) {
            service.recordAccountServiceError();
        }
        service.clearAlertSuppression("ACCOUNT_SERVICE_UNAVAILABLE");
        service.recordAccountServiceError();

        verify(auditService, times(2)).logSystemEvent(
                eq("ALERT_TRIGGERED"), eq("AlertingService"), anyString(),
                org.mockito.ArgumentMatchers.<Map<String, String>>argThat(
                        details -> "ACCOUNT_SERVICE_UNAVAILABLE".equals(details.get("alertType"))));
    }
}
