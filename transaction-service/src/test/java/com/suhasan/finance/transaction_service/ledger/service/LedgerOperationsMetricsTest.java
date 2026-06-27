package com.suhasan.finance.transaction_service.ledger.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerOperationsMetricsTest {

    @Test
    void recordsBoundedLedgerOperationsMetricsWithoutIdentityLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LedgerOperationsMetrics metrics = new LedgerOperationsMetrics(registry);

        metrics.recordPosting("TRANSFER", "USD", "posted", Duration.ofMillis(42));
        metrics.recordIdempotentReplay("TRANSFER", "USD");
        metrics.recordProjectionInvariantFailure("USD");
        metrics.updatePendingJournals(3, Duration.ofMinutes(7));
        metrics.updateSuspenseBalance("USD", new BigDecimal("12.34"));
        metrics.recordStatementGeneration("USD", "success", Duration.ofMillis(25));

        assertThat(registry.find("ledger.posting.commands").tag("operation", "TRANSFER").tag("currency", "USD").tag("outcome", "posted").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find("ledger.posting.duration").tag("operation", "TRANSFER").tag("currency", "USD").tag("outcome", "posted").timer().count())
                .isEqualTo(1L);
        assertThat(registry.find("ledger.idempotent.replays").tag("operation", "TRANSFER").tag("currency", "USD").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find("ledger.projection.invariant_failures").tag("currency", "USD").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find("ledger.pending_journals.count").gauge().value()).isEqualTo(3.0);
        assertThat(registry.find("ledger.pending_journals.oldest_age_seconds").gauge().value()).isEqualTo(420.0);
        assertThat(registry.find("ledger.suspense.balance").tag("currency", "USD").gauge().value()).isEqualTo(12.34);
        assertThat(registry.find("ledger.statement.generation.duration").tag("currency", "USD").tag("outcome", "success").timer().count())
                .isEqualTo(1L);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTag("journalId")).isNull());
    }

    @Test
    void alertRulesIncludeCriticalLedgerCutoverSignals() throws Exception {
        String alerts = Files.readString(Path.of("src/main/resources/transaction_service_alerts.yml"));

        assertThat(alerts).contains(
                "LedgerProjectionInvariantFailureCritical",
                "LedgerSuspenseBalanceNonZero",
                "LedgerStalePendingJournals",
                "LedgerProjectionOutboxBacklogSustained",
                "LedgerCriticalReconciliationExceptions");
    }
}
