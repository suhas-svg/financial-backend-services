package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "journal_transactions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JournalTransaction {
    @Id
    @Column(name = "journal_id")
    private UUID journalId;
    @Column(name = "journal_reference", nullable = false)
    private String journalReference;
    @Enumerated(EnumType.STRING)
    @Column(name = "journal_type", nullable = false)
    private JournalType journalType;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
    private String description;
    @Column(name = "correlation_id", nullable = false)
    private String correlationId;
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "idempotency_scope", nullable = false)
    private String idempotencyScope;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false)
    private String requestFingerprint;
    @Column(name = "reversal_of_journal_id")
    private UUID reversalOfJournalId;
}
