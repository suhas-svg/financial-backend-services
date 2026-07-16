package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeDomainEvent;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeNotificationDelivery;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenario;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeNotificationDeliveryRepository;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.NotificationDeliveryEvidence;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.SimulationProof;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutcomeNotificationDeliveryService {
    private final OutcomeNotificationDeliveryRepository repository;
    private final ResilientAccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;

    @Value("${outcome-protection.notifications.max-attempts:5}") private int maxAttempts;
    @Value("${outcome-protection.notifications.initial-backoff-seconds:30}") private long initialBackoffSeconds;
    @Value("${outcome-protection.notifications.max-backoff-seconds:900}") private long maxBackoffSeconds;
    @Value("${outcome-protection.notifications.sla-seconds:300}") private long slaSeconds;

    @Transactional
    public OutcomeNotificationDelivery enqueue(OutcomeDomainEvent warning, OutcomeScenario scenario, SimulationProof simulation) {
        return repository.findByWarningEventId(warning.getEventId()).orElseGet(() -> {
            String deliveryId = UUID.nameUUIDFromBytes(("balance-shield:" + warning.getEventId())
                    .getBytes(StandardCharsets.UTF_8)).toString();
            String dedupeKey = "outcome-protection:" + warning.getEventId();
            Payload payload = new Payload(scenario.getUserId(), "OUTCOME_PROTECTION_AT_RISK", "WARNING",
                    "Balance Shield needs attention",
                    "Your saved outcome may fall below %s %s on %s. Review the causal timeline and guardrail drafts."
                            .formatted(scenario.getCurrency(), simulation.baseline().protectedMinimum().toPlainString(),
                                    simulation.baseline().failureDate()),
                    "OUTCOME_PROTECTION", scenario.getScenarioId(), dedupeKey);
            return repository.save(OutcomeNotificationDelivery.builder()
                    .deliveryId(deliveryId).warningEventId(warning.getEventId()).userId(scenario.getUserId())
                    .scenarioId(scenario.getScenarioId()).dedupeKey(dedupeKey).payloadJson(json(payload))
                    .state("PENDING").attemptCount(0).nextAttemptAt(Instant.now()).build());
        });
    }

    @Transactional
    public OutcomeNotificationDelivery dispatch(String deliveryId, Instant now) {
        OutcomeNotificationDelivery delivery = repository.lockById(deliveryId).orElseThrow();
        if (delivery.getState().equals("DELIVERED") || delivery.getState().equals("TERMINAL_FAILED")
                || delivery.getNextAttemptAt().isAfter(now)) return delivery;
        if (delivery.getSlaEscalatedAt() == null && delivery.getCreatedAt().plusSeconds(slaSeconds).isBefore(now)) {
            delivery.setSlaEscalatedAt(now);
        }
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        if (delivery.getFirstAttemptAt() == null) delivery.setFirstAttemptAt(now);
        delivery.setLastAttemptAt(now);
        try {
            Payload payload = read(delivery.getPayloadJson());
            accountServiceClient.createNotification(ResilientAccountServiceClient.NotificationRequest.builder()
                    .userId(payload.userId()).type(payload.type()).severity(payload.severity())
                    .title(payload.title()).message(payload.message()).sourceType(payload.sourceType())
                    .sourceId(payload.sourceId()).dedupeKey(payload.dedupeKey()).deliveryId(delivery.getDeliveryId()).build());
            delivery.setState("DELIVERED");
            delivery.setDeliveredAt(now);
            delivery.setLastError(null);
        } catch (RuntimeException failure) {
            delivery.setLastError(sanitize(failure));
            if (delivery.getAttemptCount() >= Math.max(1, maxAttempts)) {
                delivery.setState("TERMINAL_FAILED");
                delivery.setTerminalAt(now);
                if (delivery.getSlaEscalatedAt() == null) delivery.setSlaEscalatedAt(now);
            } else {
                long multiplier = 1L << Math.min(20, delivery.getAttemptCount() - 1);
                long delay = Math.min(Math.max(1, maxBackoffSeconds), Math.max(1, initialBackoffSeconds) * multiplier);
                delivery.setState("RETRY_SCHEDULED");
                delivery.setNextAttemptAt(now.plusSeconds(delay));
            }
        }
        return repository.save(delivery);
    }

    @Transactional(readOnly = true)
    public Optional<NotificationDeliveryEvidence> latestEvidence(String userId, String scenarioId) {
        return repository.findTopByUserIdAndScenarioIdOrderByCreatedAtDesc(userId, scenarioId).map(this::evidence);
    }

    public NotificationDeliveryEvidence evidence(OutcomeNotificationDelivery delivery) {
        return new NotificationDeliveryEvidence(delivery.getDeliveryId(), delivery.getState(), delivery.getAttemptCount(),
                delivery.getNextAttemptAt(), delivery.getDeliveredAt(), delivery.getTerminalAt(),
                delivery.getSlaEscalatedAt(), delivery.getLastError(), delivery.getDedupeKey());
    }

    private String json(Payload payload) {
        try { return objectMapper.writeValueAsString(payload); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize notification delivery", ex); }
    }
    private Payload read(String json) {
        try { return objectMapper.readValue(json, Payload.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not read notification delivery", ex); }
    }
    private String sanitize(RuntimeException failure) {
        String value = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        value = value.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private record Payload(String userId, String type, String severity, String title, String message,
                           String sourceType, String sourceId, String dedupeKey) {}
}
