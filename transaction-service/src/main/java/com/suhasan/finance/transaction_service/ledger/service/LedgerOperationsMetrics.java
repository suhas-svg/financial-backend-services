package com.suhasan.finance.transaction_service.ledger.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LedgerOperationsMetrics {

    private final MeterRegistry registry;
    private final AtomicLong pendingJournals = new AtomicLong();
    private final AtomicLong oldestPendingJournalAgeSeconds = new AtomicLong();
    private final Map<String, AtomicReference<BigDecimal>> suspenseBalances = new ConcurrentHashMap<>();

    public LedgerOperationsMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("ledger.pending_journals.count", pendingJournals, AtomicLong::doubleValue)
                .description("Current number of pending ledger journals")
                .register(registry);
        Gauge.builder("ledger.pending_journals.oldest_age_seconds", oldestPendingJournalAgeSeconds, AtomicLong::doubleValue)
                .description("Age in seconds of the oldest pending ledger journal")
                .register(registry);
    }

    public void recordPosting(String operation, String currency, String outcome, Duration duration) {
        String safeOperation = bounded(operation);
        String safeCurrency = currency(currency);
        String safeOutcome = bounded(outcome);
        Counter.builder("ledger.posting.commands")
                .description("Ledger posting commands by bounded operation, currency, and outcome")
                .tag("operation", safeOperation)
                .tag("currency", safeCurrency)
                .tag("outcome", safeOutcome)
                .register(registry)
                .increment();
        Timer.builder("ledger.posting.duration")
                .description("Ledger posting duration by bounded operation, currency, and outcome")
                .tag("operation", safeOperation)
                .tag("currency", safeCurrency)
                .tag("outcome", safeOutcome)
                .register(registry)
                .record(duration);
    }

    public void recordIdempotentReplay(String operation, String currency) {
        Counter.builder("ledger.idempotent.replays")
                .description("Ledger idempotent replay count")
                .tag("operation", bounded(operation))
                .tag("currency", currency(currency))
                .register(registry)
                .increment();
    }

    public void recordProjectionInvariantFailure(String currency) {
        Counter.builder("ledger.projection.invariant_failures")
                .description("Ledger projection invariant failures")
                .tag("currency", currency(currency))
                .register(registry)
                .increment();
    }

    public void updatePendingJournals(long count, Duration oldestAge) {
        pendingJournals.set(Math.max(0, count));
        oldestPendingJournalAgeSeconds.set(Math.max(0, oldestAge.toSeconds()));
    }

    public void updateSuspenseBalance(String currency, BigDecimal amount) {
        AtomicReference<BigDecimal> holder = suspenseBalances.computeIfAbsent(currency(currency), this::registerSuspenseGauge);
        holder.set(amount == null ? BigDecimal.ZERO : amount);
    }

    public void recordStatementGeneration(String currency, String outcome, Duration duration) {
        Timer.builder("ledger.statement.generation.duration")
                .description("Monthly statement generation duration")
                .tag("currency", currency(currency))
                .tag("outcome", bounded(outcome))
                .register(registry)
                .record(duration);
    }

    private AtomicReference<BigDecimal> registerSuspenseGauge(String currency) {
        AtomicReference<BigDecimal> holder = new AtomicReference<>(BigDecimal.ZERO);
        Gauge.builder("ledger.suspense.balance", holder, value -> value.get().doubleValue())
                .description("Current suspense balance by currency")
                .tag("currency", currency)
                .register(registry);
        return holder;
    }

    private String currency(String value) {
        if (value == null || value.isBlank()) {
            return "UNK";
        }
        return value.trim().toUpperCase();
    }

    private String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
