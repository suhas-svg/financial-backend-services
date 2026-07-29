package com.suhasan.finance.transaction_service.evidence;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class FinancialEvidenceOutboxDispatcher {
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final List<FinancialEvidenceStatus> BACKLOG_STATUSES =
            List.of(FinancialEvidenceStatus.PENDING, FinancialEvidenceStatus.RETRY_SCHEDULED);

    private final FinancialEvidenceOutboxRepository outboxRepository;
    private final FinancialEvidenceDeliveryRepository deliveryRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final AtomicLong backlogGauge = new AtomicLong();
    private final AtomicLong oldestAgeSecondsGauge = new AtomicLong();
    private final AtomicLong terminalGauge = new AtomicLong();

    @Autowired
    public FinancialEvidenceOutboxDispatcher(
            FinancialEvidenceOutboxRepository outboxRepository,
            FinancialEvidenceDeliveryRepository deliveryRepository,
            MeterRegistry meterRegistry) {
        this(outboxRepository, deliveryRepository, meterRegistry, Clock.systemUTC());
    }

    FinancialEvidenceOutboxDispatcher(
            FinancialEvidenceOutboxRepository outboxRepository,
            FinancialEvidenceDeliveryRepository deliveryRepository,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.deliveryRepository = deliveryRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        meterRegistry.gauge("financial.evidence.outbox.backlog", backlogGauge);
        meterRegistry.gauge("financial.evidence.outbox.oldest_age_seconds", oldestAgeSecondsGauge);
        meterRegistry.gauge("financial.evidence.outbox.terminal_failures", terminalGauge);
    }

    @Scheduled(fixedDelayString = "${financial.evidence-outbox.dispatch-interval-ms:5000}")
    @Transactional
    public void dispatchScheduled() {
        dispatchDue(DEFAULT_BATCH_SIZE);
    }

    @Transactional
    public DispatchSummary dispatchDue(int requestedLimit) {
        int limit = Math.max(1, Math.min(DEFAULT_BATCH_SIZE, requestedLimit));
        LocalDateTime now = now();
        List<FinancialEvidenceOutbox> events = outboxRepository.claimDue(now, limit);
        int delivered = 0;
        int failed = 0;
        int terminal = 0;
        for (FinancialEvidenceOutbox event : events) {
            try {
                deliverMissingDestinations(event, now);
                event.markDelivered(now);
                delivered++;
                meterRegistry.counter("financial.evidence.outbox.delivered").increment();
            } catch (RuntimeException exception) {
                boolean terminalFailure = event.recordFailure(
                        sanitize(exception.getMessage()), nextAttempt(event, now));
                if (terminalFailure) {
                    terminal++;
                    meterRegistry.counter("financial.evidence.outbox.terminal_transitions").increment();
                } else {
                    failed++;
                    meterRegistry.counter("financial.evidence.outbox.retries").increment();
                }
            }
        }
        recordBacklogMetrics(now);
        return new DispatchSummary(events.size(), delivered, failed, terminal);
    }

    private void deliverMissingDestinations(FinancialEvidenceOutbox event, LocalDateTime now) {
        for (FinancialEvidenceDestination destination : FinancialEvidenceDestination.values()) {
            if (!deliveryRepository.existsByEventIdAndDestination(event.getEventId(), destination)) {
                deliveryRepository.save(FinancialEvidenceDelivery.create(
                        event.getEventId(), destination, event.getPayload(), now));
            }
        }
    }

    private LocalDateTime nextAttempt(FinancialEvidenceOutbox event, LocalDateTime now) {
        long delaySeconds = Math.min(3600L, 1L << Math.min(event.getAttemptCount(), 10));
        return now.plusSeconds(delaySeconds);
    }

    private void recordBacklogMetrics(LocalDateTime now) {
        backlogGauge.set(outboxRepository.countByStatusIn(BACKLOG_STATUSES));
        terminalGauge.set(outboxRepository.countByStatus(FinancialEvidenceStatus.TERMINAL_FAILED));
        oldestAgeSecondsGauge.set(outboxRepository.findOldestCreatedAtByStatusIn(BACKLOG_STATUSES)
                .map(createdAt -> Math.max(0L, Duration.between(createdAt, now).getSeconds()))
                .orElse(0L));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "delivery failed";
        }
        return message
                .replaceAll("(?i)(token|secret|password|api[_-]?key)\\s*[:=]?\\s*\\S*", "$1 [redacted]")
                .replaceAll("\\b\\d{2,}\\b", "[id]");
    }

    public record DispatchSummary(int attempted, int delivered, int failed, int terminalFailures) {
    }
}
