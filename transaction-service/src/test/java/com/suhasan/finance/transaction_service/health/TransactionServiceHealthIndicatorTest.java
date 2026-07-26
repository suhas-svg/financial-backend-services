package com.suhasan.finance.transaction_service.health;

import com.suhasan.finance.transaction_service.service.MetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionServiceHealthIndicatorTest {
    @Test
    void reportsTransactionMetricsAndWarnings() {
        MetricsService metrics = mock(MetricsService.class);
        when(metrics.getActiveTransactionsCount()).thenReturn(1001L);
        when(metrics.getDailyTransactionVolume()).thenReturn(42L);
        when(metrics.getTransactionSuccessRate()).thenReturn(0.90);

        Health result = new TransactionServiceHealthIndicator(metrics).health();

        assertThat(result.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(result.getDetails()).containsKeys("service", "version", "uptime", "jvm", "memory", "transactions");
        assertThat(result.getDetails().get("transactionWarning").toString()).contains("High number");
    }

    @Test
    void reportsMetricFailuresAsDown() {
        MetricsService metrics = mock(MetricsService.class);
        when(metrics.getActiveTransactionsCount()).thenThrow(new IllegalStateException("metrics unavailable"));

        Health result = new TransactionServiceHealthIndicator(metrics).health();

        assertThat(result.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(result.getDetails()).containsEntry("error", "metrics unavailable");
    }

    @Test
    void formatsUptimeAndByteRanges() {
        TransactionServiceHealthIndicator indicator = new TransactionServiceHealthIndicator(mock(MetricsService.class));

        assertThat((String) ReflectionTestUtils.invokeMethod(indicator, "formatUptime", 5_000L)).isEqualTo("0m 5s");
        assertThat((String) ReflectionTestUtils.invokeMethod(indicator, "formatUptime", 3_660_000L)).isEqualTo("1h 1m");
        assertThat((String) ReflectionTestUtils.invokeMethod(indicator, "formatUptime", 90_060_000L)).isEqualTo("1d 1h 1m");
        assertThat((String) ReflectionTestUtils.invokeMethod(indicator, "formatBytes", 100L)).isEqualTo("100 B");
        assertThat((String) ReflectionTestUtils.invokeMethod(indicator, "formatBytes", 2048L)).isEqualTo("2.0 KB");
    }
}
