package com.suhasan.finance.transaction_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scheduled_transfer_runs", indexes = {
        @Index(name = "idx_scheduled_transfer_runs_schedule_id", columnList = "schedule_id"),
        @Index(name = "idx_scheduled_transfer_runs_status", columnList = "status"),
        @Index(name = "idx_scheduled_transfer_runs_transaction_id", columnList = "transaction_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_scheduled_transfer_run", columnNames = {"schedule_id", "scheduled_for"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledTransferRun {

    @Id
    @Column(name = "run_id", length = 36)
    private String runId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private ScheduledTransfer schedule;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScheduledTransferRunStatus status;

    @Column(name = "transaction_id", length = 36)
    private String transactionId;

    @NotBlank
    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @PrePersist
    void onCreate() {
        if (runId == null) {
            runId = UUID.randomUUID().toString();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        if (status == null) {
            status = ScheduledTransferRunStatus.PROCESSING;
        }
    }
}
