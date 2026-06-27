package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReconciliationRun {

    @Id
    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_type", nullable = false, length = 40)
    private ReconciliationType reconciliationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ReconciliationRunStatus status;

    @Column(name = "requested_by", nullable = false, length = 120)
    private String requestedBy;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "total_exceptions", nullable = false)
    private int totalExceptions;

    @Column(name = "critical_exceptions", nullable = false)
    private int criticalExceptions;

    public static ReconciliationRun start(LocalDate businessDate, ReconciliationType type, String requestedBy) {
        return ReconciliationRun.builder()
                .runId(UUID.randomUUID())
                .businessDate(businessDate)
                .reconciliationType(type)
                .status(ReconciliationRunStatus.RUNNING)
                .requestedBy(requestedBy)
                .startedAt(LocalDateTime.now())
                .build();
    }

    public void complete(int totalExceptions, int criticalExceptions) {
        this.totalExceptions = totalExceptions;
        this.criticalExceptions = criticalExceptions;
        this.status = totalExceptions == 0
                ? ReconciliationRunStatus.COMPLETED
                : ReconciliationRunStatus.COMPLETED_WITH_EXCEPTIONS;
        this.completedAt = LocalDateTime.now();
    }
}
