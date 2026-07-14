package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_check_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReconciliationCheckResult {
    @Id
    @Column(name = "check_result_id", nullable = false)
    private UUID checkResultId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_code", nullable = false, length = 80)
    private ReconciliationCheckCode checkCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private ReconciliationSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationCheckStatus status;

    @Column(name = "checked_count", nullable = false)
    private int checkedCount;

    @Column(name = "exception_count", nullable = false)
    private int exceptionCount;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ReconciliationCheckResult completed(
            UUID runId,
            ReconciliationCheckCode checkCode,
            ReconciliationSeverity severity,
            int checkedCount,
            int exceptionCount,
            String summary) {
        return ReconciliationCheckResult.builder()
                .checkResultId(UUID.randomUUID())
                .runId(runId)
                .checkCode(checkCode)
                .severity(severity)
                .status(exceptionCount == 0 ? ReconciliationCheckStatus.PASSED : ReconciliationCheckStatus.FAILED)
                .checkedCount(checkedCount)
                .exceptionCount(exceptionCount)
                .summary(summary)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
