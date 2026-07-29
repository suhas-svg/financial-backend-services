package com.suhasan.finance.transaction_service.evidence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "financial_evidence_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialEvidenceOutbox {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialEvidenceStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static FinancialEvidenceOutbox create(
            UUID eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String idempotencyKey,
            String payload,
            LocalDateTime createdAt,
            int maxAttempts) {
        FinancialEvidenceOutbox event = new FinancialEvidenceOutbox();
        event.eventId = eventId;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.idempotencyKey = idempotencyKey;
        event.payload = payload;
        event.status = FinancialEvidenceStatus.PENDING;
        event.maxAttempts = maxAttempts;
        event.nextAttemptAt = createdAt;
        event.createdAt = createdAt;
        return event;
    }

    public void markDelivered(LocalDateTime now) {
        status = FinancialEvidenceStatus.DELIVERED;
        deliveredAt = now;
        lastError = null;
    }

    public boolean recordFailure(String error, LocalDateTime nextAttempt) {
        attemptCount++;
        lastError = truncate(error);
        if (attemptCount >= maxAttempts) {
            status = FinancialEvidenceStatus.TERMINAL_FAILED;
            return true;
        }
        status = FinancialEvidenceStatus.RETRY_SCHEDULED;
        nextAttemptAt = nextAttempt;
        return false;
    }

    private String truncate(String value) {
        String safe = value == null || value.isBlank() ? "delivery failed" : value;
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }
}
