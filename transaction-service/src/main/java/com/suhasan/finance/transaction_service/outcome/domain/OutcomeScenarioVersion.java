package com.suhasan.finance.transaction_service.outcome.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "outcome_scenario_versions")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutcomeScenarioVersion {
    @Id @Column(name = "version_id", length = 36)
    private String versionId;
    @Column(name = "scenario_id", nullable = false, length = 36)
    private String scenarioId;
    @Column(name = "scenario_version", nullable = false)
    private int scenarioVersion;
    @Column(name = "horizon_start", nullable = false)
    private LocalDate horizonStart;
    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;
    @Column(name = "protected_minimum", nullable = false, precision = 19, scale = 2)
    private BigDecimal protectedMinimum;
    @Column(name = "account_ids_json", nullable = false, columnDefinition = "TEXT")
    private String accountIdsJson;
    @Column(name = "assumptions_json", nullable = false, columnDefinition = "TEXT")
    private String assumptionsJson;
    @Column(name = "shocks_json", nullable = false, columnDefinition = "TEXT")
    private String shocksJson;
    @Column(name = "ledger_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String ledgerSnapshotJson;
    @Column(name = "schedule_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String scheduleSnapshotJson;
    @Column(name = "source_fingerprint", nullable = false, length = 64)
    private String sourceFingerprint;
    @Column(name = "mutation_idempotency_key", nullable = false, length = 128)
    private String mutationIdempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void create() { if (createdAt == null) createdAt = Instant.now(); }
}
