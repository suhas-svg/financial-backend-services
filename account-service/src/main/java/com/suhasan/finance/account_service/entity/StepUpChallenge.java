package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "step_up_challenges", indexes = {
        @Index(name = "idx_step_up_user_status", columnList = "user_id,status"),
        @Index(name = "idx_step_up_expiry", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class StepUpChallenge {
    @Id
    @Column(name = "challenge_id", length = 36)
    private String challengeId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "action_fingerprint", nullable = false, length = 64)
    private String actionFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepUpChallengeStatus status = StepUpChallengeStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "proof_hash", length = 64)
    private String proofHash;

    @Column(name = "consumer_key", length = 100)
    private String consumerKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "proof_expires_at")
    private Instant proofExpiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    void onCreate() {
        if (challengeId == null) {
            challengeId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (version == null) {
            version = 0L;
        }
    }
}
