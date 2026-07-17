package com.suhasan.finance.transaction_service.outcome.service;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeGuardrailControlEvent;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeGuardrailRuntimeControl;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeGuardrailControlEventRepository;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeGuardrailRuntimeControlRepository;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutcomeGuardrailControlService {
    public static final String GLOBAL_CONTROL_ID = "GLOBAL";

    private final OutcomeGuardrailRuntimeControlRepository controlRepository;
    private final OutcomeGuardrailControlEventRepository eventRepository;

    @Transactional(readOnly = true)
    public GuardrailControlResponse current() {
        return response(requiredControl());
    }

    @Transactional
    public GuardrailControlResponse update(GuardrailControlUpdateRequest request, String actor, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        String reason = request.reason().trim();
        String requestFingerprint = hash(request.executionEnabled() + "|" + reason);
        var replay = eventRepository.findByActorAndIdempotencyKey(actor, key);
        if (replay.isPresent()) {
            requireFingerprint(replay.get(), requestFingerprint);
            var event = replay.get();
            return new GuardrailControlResponse(event.isExecutionEnabled(), event.getReason(),
                    event.getActor(), event.getCreatedAt());
        }

        OutcomeGuardrailRuntimeControl control = controlRepository.lockById(GLOBAL_CONTROL_ID)
                .orElseThrow(() -> new IllegalStateException("Guardrail execution control is not configured"));
        control.setExecutionEnabled(request.executionEnabled());
        control.setReason(reason);
        control.setChangedBy(actor);
        control.setUpdatedAt(Instant.now());
        controlRepository.save(control);
        eventRepository.save(OutcomeGuardrailControlEvent.builder()
                .eventId(UUID.randomUUID().toString()).executionEnabled(request.executionEnabled())
                .reason(reason).actor(actor).idempotencyKey(key)
                .requestFingerprint(requestFingerprint).build());
        return response(control);
    }

    @Transactional(readOnly = true)
    public List<GuardrailControlEventResponse> events() {
        return eventRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(event -> new GuardrailControlEventResponse(event.getEventId(), event.isExecutionEnabled(),
                        event.getReason(), event.getActor(), event.getCreatedAt()))
                .toList();
    }

    OutcomeGuardrailRuntimeControl requiredControl() {
        return controlRepository.findById(GLOBAL_CONTROL_ID)
                .orElseThrow(() -> new IllegalStateException("Guardrail execution control is not configured"));
    }

    private GuardrailControlResponse response(OutcomeGuardrailRuntimeControl control) {
        return new GuardrailControlResponse(control.isExecutionEnabled(), control.getReason(),
                control.getChangedBy(), control.getUpdatedAt());
    }

    private String requireKey(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
        String key = value.trim();
        if (key.length() > 128) throw new IllegalArgumentException("Idempotency-Key is too long");
        return key;
    }

    private void requireFingerprint(OutcomeGuardrailControlEvent event, String fingerprint) {
        if (!event.getRequestFingerprint().equals(fingerprint)) {
            throw new IllegalStateException("Idempotency-Key was already used for a different control change");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fingerprint guardrail control", e);
        }
    }
}
