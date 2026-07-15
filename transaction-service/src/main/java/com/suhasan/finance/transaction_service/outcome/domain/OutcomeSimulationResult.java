package com.suhasan.finance.transaction_service.outcome.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "outcome_simulation_results")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutcomeSimulationResult {
    @Id @Column(name = "result_id", length = 36)
    private String resultId;
    @Column(name = "scenario_id", nullable = false, length = 36)
    private String scenarioId;
    @Column(name = "scenario_version", nullable = false)
    private int scenarioVersion;
    @Column(name = "baseline_safe", nullable = false)
    private boolean baselineSafe;
    @Column(name = "baseline_lowest_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal baselineLowestBalance;
    @Column(name = "baseline_failure_date")
    private LocalDate baselineFailureDate;
    @Column(name = "proof_json", nullable = false, columnDefinition = "TEXT")
    private String proofJson;
    @Column(name = "failure_json", columnDefinition = "TEXT")
    private String failureJson;
    @Column(name = "repair_json", nullable = false, columnDefinition = "TEXT")
    private String repairJson;
    @Column(name = "evaluated_combinations", nullable = false)
    private int evaluatedCombinations;
    @Column(name = "search_capped", nullable = false)
    private boolean searchCapped;
    @Column(name = "result_fingerprint", nullable = false, length = 64)
    private String resultFingerprint;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void create() { if (createdAt == null) createdAt = Instant.now(); }
}
