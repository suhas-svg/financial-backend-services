package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_exceptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReconciliationException {

    @Id
    @Column(name = "exception_id", nullable = false)
    private UUID exceptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_code", nullable = false, length = 80)
    private ReconciliationCheckCode checkCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private ReconciliationSeverity severity;

    @Column(name = "fingerprint", nullable = false, length = 240, unique = true)
    private String fingerprint;

    @Column(name = "journal_id")
    private UUID journalId;

    @Column(name = "ledger_account_id")
    private UUID ledgerAccountId;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ReconciliationExceptionStatus status;

    @Column(name = "assigned_to", length = 120)
    private String assignedTo;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static ReconciliationException open(
            ReconciliationCheckCode checkCode,
            ReconciliationSeverity severity,
            String fingerprint,
            UUID journalId,
            UUID ledgerAccountId,
            String summary) {
        LocalDateTime now = LocalDateTime.now();
        return ReconciliationException.builder()
                .exceptionId(UUID.randomUUID())
                .checkCode(checkCode)
                .severity(severity)
                .fingerprint(fingerprint)
                .journalId(journalId)
                .ledgerAccountId(ledgerAccountId)
                .summary(summary)
                .status(ReconciliationExceptionStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void updateStatus(ReconciliationExceptionStatus status, String note, String actor, long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new IllegalArgumentException("Exception version conflict");
        }
        if ((status == ReconciliationExceptionStatus.RESOLVED || status == ReconciliationExceptionStatus.WAIVED)
                && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("Resolution note is required");
        }
        this.status = status;
        if (note != null && !note.isBlank()) {
            this.resolutionNote = note;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void assignTo(String assignee, String actor, long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new IllegalArgumentException("Exception version conflict");
        }
        if (assignee == null || assignee.isBlank()) {
            throw new IllegalArgumentException("Assignee is required");
        }
        this.assignedTo = assignee;
        if (this.status == ReconciliationExceptionStatus.OPEN
                || this.status == ReconciliationExceptionStatus.ACKNOWLEDGED) {
            this.status = ReconciliationExceptionStatus.IN_PROGRESS;
        }
        this.updatedAt = LocalDateTime.now();
    }
}
