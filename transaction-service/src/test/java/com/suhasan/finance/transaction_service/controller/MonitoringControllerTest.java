package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.service.AlertingService;
import com.suhasan.finance.transaction_service.service.MetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringControllerTest {
    @Mock MetricsService metricsService;
    @Mock AlertingService alertingService;

    private SimpleMeterRegistry meterRegistry;
    private MonitoringController controller;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        controller = new MonitoringController(metricsService, alertingService, meterRegistry);
    }

    @Test
    void detailedHealthCombinesTransactionAlertAndSystemMetrics() {
        when(metricsService.getTransactionSuccessRate()).thenReturn(0.975d);
        when(metricsService.getDailyTransactionVolume()).thenReturn(120L);
        when(metricsService.getDailyTransactionAmount()).thenReturn(new BigDecimal("3456.78"));
        when(metricsService.getActiveTransactionsCount()).thenReturn(3L);
        when(alertingService.getAlertStatistics()).thenReturn(Map.of("criticalAlerts", 0.0d));
        Gauge.builder("jvm.memory.used", new AtomicInteger(512), AtomicInteger::doubleValue)
                .register(meterRegistry);
        Gauge.builder("jvm.memory.max", new AtomicInteger(1024), AtomicInteger::doubleValue)
                .register(meterRegistry);

        var response = controller.getDetailedHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP").containsEntry("service", "transaction-service");
        assertThat(map(response.getBody().get("transactions")))
                .containsEntry("successRate", "97.50%")
                .containsEntry("dailyVolume", 120L)
                .containsEntry("activeTransactions", 3L);
        assertThat(map(response.getBody().get("alerts"))).containsEntry("criticalAlerts", 0.0d);
        assertThat(map(response.getBody().get("system")))
                .containsEntry("jvmMemoryUsed", 512L)
                .containsEntry("jvmMemoryMax", 1024L);
    }

    @Test
    void detailedHealthReportsDownWhenMetricsCannotBeRead() {
        when(metricsService.getTransactionSuccessRate()).thenThrow(new IllegalStateException("metrics unavailable"));

        var response = controller.getDetailedHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .containsEntry("status", "DOWN")
                .containsEntry("error", "metrics unavailable");
    }

    @Test
    void transactionStatsExposeBusinessCountersErrorsAndTimers() {
        when(metricsService.getTransactionSuccessRate()).thenReturn(0.8d);
        when(metricsService.getDailyTransactionVolume()).thenReturn(10L);
        when(metricsService.getDailyTransactionAmount()).thenReturn(new BigDecimal("100.00"));
        when(metricsService.getActiveTransactionsCount()).thenReturn(2L);
        Counter.builder("transaction.initiated.total").register(meterRegistry).increment(5);
        Counter.builder("transaction.completed.total").register(meterRegistry).increment(4);
        Counter.builder("transaction.failed.total").register(meterRegistry).increment();
        Counter.builder("transaction.error.insufficient_funds.total").register(meterRegistry).increment(2);
        Timer.builder("transaction.processing.duration").register(meterRegistry)
                .record(Duration.ofMillis(250));

        var response = controller.getTransactionStats();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("successRate", 0.8d)
                .containsEntry("dailyVolume", 10L)
                .containsEntry("totalInitiated", 5.0d)
                .containsEntry("totalCompleted", 4.0d)
                .containsEntry("totalFailed", 1.0d)
                .containsEntry("totalReversed", 0.0d);
        assertThat(map(response.getBody().get("errors")))
                .containsEntry("insufficientFunds", 2.0d)
                .containsEntry("accountNotFound", 0.0d);
        assertThat(map(response.getBody().get("performance")))
                .containsEntry("avgProcessingTime", 250.0d)
                .containsEntry("avgAccountValidationTime", 0.0d);
    }

    @Test
    void systemStatsReturnZerosForMissingMetersAndValuesForRegisteredGauges() {
        Gauge.builder("jvm.memory.used", new AtomicInteger(256), AtomicInteger::doubleValue)
                .register(meterRegistry);
        Gauge.builder("jvm.memory.max", new AtomicInteger(1024), AtomicInteger::doubleValue)
                .register(meterRegistry);
        Gauge.builder("system.cpu.usage", new AtomicInteger(1), value -> 0.25d)
                .register(meterRegistry);
        Gauge.builder("process.uptime", new AtomicInteger(90), AtomicInteger::doubleValue)
                .register(meterRegistry);

        var response = controller.getSystemStats();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(map(response.getBody().get("jvm")))
                .containsEntry("memoryUsed", 256L)
                .containsEntry("memoryMax", 1024L)
                .containsEntry("memoryUsagePercent", 25.0d)
                .containsEntry("threadsActive", 0.0d);
        assertThat(map(response.getBody().get("system")))
                .containsEntry("cpuUsage", 25.0d)
                .containsEntry("uptime", 90L);
        assertThat(map(response.getBody().get("database")))
                .containsEntry("connectionPoolActive", 0.0d)
                .containsEntry("avgQueryTime", 0.0d);
    }

    @Test
    void alertEndpointsReturnStateClearSuppressionAndReportFailures() {
        when(alertingService.getAlertStatistics()).thenReturn(Map.of("warningAlerts", 2.0d));

        var status = controller.getAlertStatus();
        var cleared = controller.clearAlertSuppression("HIGH_ERROR_RATE");

        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody())
                .containsEntry("warningAlerts", 2.0d)
                .containsEntry("alertingEnabled", true);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().get("message")).contains("HIGH_ERROR_RATE");
        verify(alertingService).clearAlertSuppression("HIGH_ERROR_RATE");

        doThrow(new IllegalStateException("cannot clear"))
                .when(alertingService).clearAlertSuppression("BROKEN");
        var failed = controller.clearAlertSuppression("BROKEN");
        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(failed.getBody()).containsEntry("error", "cannot clear");
    }

    @Test
    void availableMetricsAreDeduplicatedAndGroupedByCategory() {
        Counter.builder("transaction.completed.total").register(meterRegistry).increment();
        Counter.builder("account.service.error.total").register(meterRegistry).increment();
        Gauge.builder("hikaricp.connections.active", new AtomicInteger(2), AtomicInteger::doubleValue)
                .register(meterRegistry);
        Counter.builder("custom.metric").register(meterRegistry).increment();

        var response = controller.getAvailableMetrics();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("totalMeters", 4);
        Map<String, Object> categories = map(response.getBody().get("categories"));
        assertThat(list(categories.get("transaction"))).contains("transaction.completed.total");
        assertThat(list(categories.get("account"))).contains("account.service.error.total");
        assertThat(list(categories.get("database"))).contains("hikaricp.connections.active");
        assertThat(list(categories.get("other"))).contains("custom.metric");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Object value) {
        return (List<String>) value;
    }
}
