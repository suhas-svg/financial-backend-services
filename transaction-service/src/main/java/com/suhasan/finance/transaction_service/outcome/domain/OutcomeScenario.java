package com.suhasan.finance.transaction_service.outcome.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "outcome_scenarios")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutcomeScenario {
    @Id @Column(name = "scenario_id", length = 36)
    private String scenarioId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "current_version", nullable = false)
    private int currentVersion;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;
    @Column(name = "create_idempotency_key", nullable = false, length = 128)
    private String createIdempotencyKey;
    @Column(name = "create_request_fingerprint", nullable = false, length = 64)
    private String createRequestFingerprint;
    @Column(name = "last_source_fingerprint", length = 64)
    private String lastSourceFingerprint;
    @Column(name = "last_protection_state", length = 24)
    private String lastProtectionState;
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version @Column(nullable = false)
    private Long version;

    @PrePersist
    void create() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "ACTIVE";
        if (version == null) version = 0L;
    }

    @PreUpdate
    void update() { updatedAt = Instant.now(); }
}
