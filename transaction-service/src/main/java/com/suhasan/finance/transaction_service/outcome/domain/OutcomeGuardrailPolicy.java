package com.suhasan.finance.transaction_service.outcome.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "outcome_guardrail_policies")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutcomeGuardrailPolicy {
    @Id @Column(name = "policy_id", length = 36) private String policyId;
    @Column(name = "guardrail_id", nullable = false, unique = true, length = 36) private String guardrailId;
    @Column(name = "scenario_id", nullable = false, length = 36) private String scenarioId;
    @Column(name = "result_id", nullable = false, length = 36) private String resultId;
    @Column(name = "user_id", nullable = false, length = 128) private String userId;
    @Column(name = "funding_account_id", nullable = false, length = 64) private String fundingAccountId;
    @Column(name = "protected_account_id", nullable = false, length = 64) private String protectedAccountId;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "trigger_threshold", nullable = false, precision = 19, scale = 2) private BigDecimal triggerThreshold;
    @Column(name = "max_action_amount", nullable = false, precision = 19, scale = 2) private BigDecimal maxActionAmount;
    @Column(name = "total_limit", nullable = false, precision = 19, scale = 2) private BigDecimal totalLimit;
    @Column(name = "total_executed", nullable = false, precision = 19, scale = 2) private BigDecimal totalExecuted;
    @Column(name = "total_reserved", nullable = false, precision = 19, scale = 2) private BigDecimal totalReserved;
    @Column(name = "max_executions", nullable = false) private int maxExecutions;
    @Column(name = "execution_count", nullable = false) private int executionCount;
    @Column(name = "terms_version", nullable = false, length = 64) private String termsVersion;
    @Column(name = "terms_hash", nullable = false, length = 64) private String termsHash;
    @Column(name = "consent_evidence_json", nullable = false, columnDefinition = "TEXT") private String consentEvidenceJson;
    @Column(name = "consent_idempotency_key", nullable = false, length = 128) private String consentIdempotencyKey;
    @Column(name = "consent_request_fingerprint", nullable = false, length = 64) private String consentRequestFingerprint;
    @Column(name = "activation_challenge_id", nullable = false, length = 36) private String activationChallengeId;
    @Column(name = "activation_challenge_expires_at", nullable = false) private Instant activationChallengeExpiresAt;
    @Column(name = "activation_fingerprint", nullable = false, length = 64) private String activationFingerprint;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consented_at", nullable = false) private Instant consentedAt;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "suspended_at") private Instant suspendedAt;
    @Column(name = "suspension_reason", length = 500) private String suspensionReason;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revocation_reason", length = 500) private String revocationReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private Long version;

    @PrePersist void create() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (consentedAt == null) consentedAt = now;
        if (totalExecuted == null) totalExecuted = BigDecimal.ZERO.setScale(2);
        if (totalReserved == null) totalReserved = BigDecimal.ZERO.setScale(2);
        if (status == null) status = "CONSENT_PENDING";
        if (version == null) version = 0L;
    }
    @PreUpdate void update() { updatedAt = Instant.now(); }
}
