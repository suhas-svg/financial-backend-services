package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerProjectionOutbox;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerProjectionOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class LedgerProjectionOutboxDispatcher {

    private static final int DEFAULT_BATCH_SIZE = 100;

    private final LedgerProjectionOutboxRepository outboxRepository;
    private final ResilientAccountServiceClient accountServiceClient;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final AtomicLong backlogGauge = new AtomicLong();
    private final AtomicLong oldestAgeSecondsGauge = new AtomicLong();

    @Autowired
    public LedgerProjectionOutboxDispatcher(
            LedgerProjectionOutboxRepository outboxRepository,
            ResilientAccountServiceClient accountServiceClient,
            MeterRegistry meterRegistry) {
        this(outboxRepository, accountServiceClient, meterRegistry, Clock.systemUTC());
    }

    LedgerProjectionOutboxDispatcher(
            LedgerProjectionOutboxRepository outboxRepository,
            ResilientAccountServiceClient accountServiceClient,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.accountServiceClient = accountServiceClient;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        meterRegistry.gauge("ledger.projection_outbox.backlog", backlogGauge);
        meterRegistry.gauge("ledger.projection_outbox.oldest_age_seconds", oldestAgeSecondsGauge);
    }

    @Scheduled(fixedDelayString = "${ledger.projection-outbox.dispatch-interval-ms:5000}")
    public void dispatchScheduled() {
        dispatchDue(DEFAULT_BATCH_SIZE);
    }

    @Transactional
    public DispatchSummary dispatchDue(int limit) {
        LocalDateTime now = now();
        List<LedgerProjectionOutbox> messages = outboxRepository.claimDue(now, limit);
        int delivered = 0;
        int failed = 0;
        int terminalFailures = 0;
        for (LedgerProjectionOutbox message : messages) {
            try {
                accountServiceClient.applyLedgerProjection(
                        message.getExternalAccountId(),
                        toRequest(message, now));
                message.markDelivered(now);
                delivered++;
                meterRegistry.counter("ledger.projection_outbox.delivered").increment();
            } catch (ResilientAccountServiceClient.LedgerProjectionDeliveryException exception) {
                if (exception.terminal()) {
                    message.markTerminalFailure(sanitize(exception.getMessage()), now);
                    terminalFailures++;
                    meterRegistry.counter("ledger.projection_outbox.terminal_failures").increment();
                } else {
                    message.markTransientFailure(sanitize(exception.getMessage()), nextAttempt(message, now));
                    failed++;
                    meterRegistry.counter("ledger.projection_outbox.failures").increment();
                }
            } catch (RuntimeException exception) {
                message.markTransientFailure(sanitize(exception.getMessage()), nextAttempt(message, now));
                failed++;
                meterRegistry.counter("ledger.projection_outbox.failures").increment();
            }
        }
        recordBacklogMetrics(now);
        return new DispatchSummary(messages.size(), delivered, failed, terminalFailures);
    }

    private ResilientAccountServiceClient.LedgerProjectionUpdateRequest toRequest(
            LedgerProjectionOutbox message,
            LocalDateTime now) {
        return new ResilientAccountServiceClient.LedgerProjectionUpdateRequest(
                message.getPostedBalance(),
                message.getPendingBalance(),
                message.getAvailableBalance(),
                message.getCurrency(),
                message.getProjectionVersion(),
                message.getSourceEventId().toString(),
                now);
    }

    private LocalDateTime nextAttempt(LedgerProjectionOutbox message, LocalDateTime now) {
        long delaySeconds = Math.min(3600L, 1L << Math.min(message.getAttemptCount(), 10));
        return now.plusSeconds(delaySeconds);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void recordBacklogMetrics(LocalDateTime now) {
        backlogGauge.set(outboxRepository.countByDeliveredAtIsNull());
        oldestAgeSecondsGauge.set(outboxRepository.findOldestUndeliveredCreatedAt()
                .map(createdAt -> Math.max(0L, Duration.between(createdAt, now).getSeconds()))
                .orElse(0L));
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
