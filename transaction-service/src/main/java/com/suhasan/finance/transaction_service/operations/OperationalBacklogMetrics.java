package com.suhasan.finance.transaction_service.operations;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class OperationalBacklogMetrics {
    private final JdbcTemplate jdbc;
    private final long staleSeconds;

    public OperationalBacklogMetrics(
            JdbcTemplate jdbc,
            MeterRegistry registry,
            @Value("${scheduled-transfer.processing-stale-seconds:300}") long staleSeconds) {
        this.jdbc = jdbc;
        this.staleSeconds = Math.max(60, staleSeconds);
        Gauge.builder("scheduled.transfer.stuck.processing", this, OperationalBacklogMetrics::stuckProcessing)
                .register(registry);
        Gauge.builder("scheduled.transfer.due.oldest.age.seconds", this, OperationalBacklogMetrics::oldestDueAge)
                .register(registry);
        Gauge.builder("financial.operations.failed.current", this, OperationalBacklogMetrics::failedOperations)
                .register(registry);
        Gauge.builder("financial.operations.stale.claims", this, OperationalBacklogMetrics::staleClaims)
                .register(registry);
    }

    double stuckProcessing() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM scheduled_transfer_runs
                 WHERE status='PROCESSING' AND started_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                """, Long.class, staleSeconds);
        return count == null ? 0D : count.doubleValue();
    }

    double oldestDueAge() {
        Instant oldest = jdbc.queryForObject("""
                SELECT MIN(next_run_at) FROM scheduled_transfers WHERE status='ACTIVE' AND next_run_at < CURRENT_TIMESTAMP
                """, (rs, row) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant());
        return oldest == null ? 0D : Math.max(0, Duration.between(oldest, Instant.now()).toSeconds());
    }

    double failedOperations() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM financial_operation_runs WHERE status='FAILED'",
                Long.class);
        return count == null ? 0D : count.doubleValue();
    }

    double staleClaims() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM financial_operation_runs
                 WHERE status='RUNNING' AND claim_until < CURRENT_TIMESTAMP
                """, Long.class);
        return count == null ? 0D : count.doubleValue();
    }
}
