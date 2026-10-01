package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeDomainEvent;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenario;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeDomainEventRepository;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeScenarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks whether the monitor can keep a scenario current.
 *
 * <p>A failed evaluation backs the scenario off exponentially ({@link #backoffAfter}) so a scenario that
 * keeps failing cannot hold its place at the front of every monitor run, and after
 * {@link #DEGRADED_AFTER_FAILURES} consecutive failures it is marked degraded with an evidence event.
 * Any successful evaluation clears this state, including a customer's manual "Check current state".
 */
@Component
public class OutcomeMonitorHealth {

    static final int DEGRADED_AFTER_FAILURES = 5;
    static final Duration FIRST_BACKOFF = Duration.ofMinutes(5);
    static final Duration MAX_BACKOFF = Duration.ofHours(24);
    private static final int MAX_ERROR_LENGTH = 500;

    private final OutcomeScenarioRepository scenarioRepository;
    private final OutcomeDomainEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OutcomeMonitorHealth(OutcomeScenarioRepository scenarioRepository,
                                OutcomeDomainEventRepository eventRepository,
                                ObjectMapper objectMapper) {
        this(scenarioRepository, eventRepository, objectMapper, Clock.systemUTC());
    }

    OutcomeMonitorHealth(OutcomeScenarioRepository scenarioRepository,
                         OutcomeDomainEventRepository eventRepository,
                         ObjectMapper objectMapper, Clock clock) {
        this.scenarioRepository = scenarioRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Records a failed monitor evaluation. Runs in its own transaction because the evaluation's
     * transaction has already rolled back.
     */
    @Transactional
    public void recordFailure(String scenarioId, RuntimeException failure) {
        scenarioRepository.findById(scenarioId).ifPresent(scenario -> {
            Instant now = clock.instant();
            int failures = scenario.getMonitorFailureCount() + 1;
            scenario.setMonitorFailureCount(failures);
            scenario.setMonitorNextAttemptAt(now.plus(backoffAfter(failures)));
            scenario.setMonitorLastError(describe(failure));
            if (failures >= DEGRADED_AFTER_FAILURES && scenario.getMonitorDegradedAt() == null) {
                scenario.setMonitorDegradedAt(now);
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("consecutiveFailures", failures);
                fields.put("lastError", scenario.getMonitorLastError());
                fields.put("nextAttemptAt", scenario.getMonitorNextAttemptAt().toString());
                record("MONITORING_DEGRADED", scenario, now, fields);
            }
        });
    }

    /** Clears failure state after a successful evaluation. Runs inside the evaluation's transaction. */
    public void recordSuccess(OutcomeScenario scenario) {
        if (scenario.getMonitorFailureCount() == 0 && scenario.getMonitorNextAttemptAt() == null
                && scenario.getMonitorDegradedAt() == null) {
            return;
        }
        if (scenario.getMonitorDegradedAt() != null) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("failuresBeforeRecovery", scenario.getMonitorFailureCount());
            fields.put("degradedAt", scenario.getMonitorDegradedAt().toString());
            record("MONITORING_RECOVERED", scenario, clock.instant(), fields);
        }
        scenario.setMonitorFailureCount(0);
        scenario.setMonitorNextAttemptAt(null);
        scenario.setMonitorLastError(null);
        scenario.setMonitorDegradedAt(null);
    }

    /** 5 min, 10 min, 20 min, ... capped at 24 hours. */
    static Duration backoffAfter(int failures) {
        int doublings = Math.max(0, Math.min(failures - 1, 20));
        Duration delay = FIRST_BACKOFF.multipliedBy(1L << doublings);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getMessage().replaceAll("\\s+", " ").trim();
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private void record(String type, OutcomeScenario scenario, Instant at, Map<String, Object> fields) {
        String fieldsJson;
        try {
            fieldsJson = objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize monitor health evidence", e);
        }
        eventRepository.save(OutcomeDomainEvent.builder()
                .eventId(UUID.randomUUID().toString()).eventType(type)
                .userId(scenario.getUserId()).scenarioId(scenario.getScenarioId())
                .scenarioVersion(scenario.getCurrentVersion())
                .dedupeKey(type.toLowerCase(Locale.ROOT) + ":" + scenario.getScenarioId() + ":" + at.toEpochMilli())
                .fieldsJson(fieldsJson).build());
    }
}
