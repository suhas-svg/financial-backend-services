package com.suhasan.finance.transaction_service.outcome.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "outcome_guardrail_drafts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutcomeGuardrailDraft {
    @Id @Column(name = "guardrail_id", length = 36)
    private String guardrailId;
    @Column(name = "scenario_id", nullable = false, length = 36)
    private String scenarioId;
    @Column(name = "result_id", nullable = false, length = 36)
    private String resultId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(name = "guardrail_type", nullable = false, length = 48)
    private String guardrailType;
    @Column(name = "threshold_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal thresholdAmount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "scope_json", nullable = false, columnDefinition = "TEXT")
    private String scopeJson;
    @Column(name = "preview_text", nullable = false, length = 1000)
    private String previewText;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "accepted_at")
    private Instant acceptedAt;
    @Column(name = "acceptance_idempotency_key", length = 128)
    private String acceptanceIdempotencyKey;
    @Column(name = "acceptance_fingerprint", length = 64)
    private String acceptanceFingerprint;
    @Column(name = "alternative_rank")
    private Integer alternativeRank;
    @Column(name = "candidate_actions_json", nullable = false, columnDefinition = "TEXT")
    private String candidateActionsJson;
    @Column(name = "replay_proof_json", columnDefinition = "TEXT")
    private String replayProofJson;
    @Column(name = "replay_certificate_hash", length = 64)
    private String replayCertificateHash;
    @Column(name = "ranking_factors_json", columnDefinition = "TEXT")
    private String rankingFactorsJson;
    @Column(name = "rejection_reasons_json", nullable = false, columnDefinition = "TEXT")
    private String rejectionReasonsJson;
    @Column(name = "preview_selected_at")
    private Instant previewSelectedAt;
    @Column(name = "preview_selection_idempotency_key", length = 128)
    private String previewSelectionIdempotencyKey;
    @Column(name = "preview_selection_fingerprint", length = 64)
    private String previewSelectionFingerprint;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version @Column(nullable = false)
    private Long version;

    @PrePersist
    void create() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = "DRAFT";
        if (candidateActionsJson == null) candidateActionsJson = "[]";
        if (rejectionReasonsJson == null) rejectionReasonsJson = "[]";
        if (version == null) version = 0L;
    }
}
