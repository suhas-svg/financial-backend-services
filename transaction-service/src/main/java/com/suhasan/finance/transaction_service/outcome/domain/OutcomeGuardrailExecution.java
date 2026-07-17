package com.suhasan.finance.transaction_service.outcome.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "outcome_guardrail_executions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutcomeGuardrailExecution {
    @Id @Column(name = "execution_id", length = 36) private String executionId;
    @Column(name = "policy_id", nullable = false, length = 36) private String policyId;
    @Column(name = "guardrail_id", nullable = false, length = 36) private String guardrailId;
    @Column(name = "user_id", nullable = false, length = 128) private String userId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "transfer_idempotency_key", nullable = false, unique = true, length = 128) private String transferIdempotencyKey;
    @Column(name = "transfer_authorization_id", length = 36) private String transferAuthorizationId;
    @Column(name = "transaction_id", length = 36) private String transactionId;
    @Column(name = "authorization_challenge_id", length = 36) private String authorizationChallengeId;
    @Column(name = "authorization_expires_at") private Instant authorizationExpiresAt;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Version @Column(nullable = false) private Long version;

    @PrePersist void create() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "REQUESTED";
        if (version == null) version = 0L;
    }
    @PreUpdate void update() { updatedAt = Instant.now(); }
}
