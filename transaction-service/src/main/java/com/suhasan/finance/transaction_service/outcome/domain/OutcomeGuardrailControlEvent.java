package com.suhasan.finance.transaction_service.outcome.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "outcome_guardrail_control_events")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutcomeGuardrailControlEvent {
    @Id @Column(name = "event_id", length = 36) private String eventId;
    @Column(name = "execution_enabled", nullable = false) private boolean executionEnabled;
    @Column(nullable = false, length = 500) private String reason;
    @Column(nullable = false, length = 128) private String actor;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void create() { if (createdAt == null) createdAt = Instant.now(); }
}
