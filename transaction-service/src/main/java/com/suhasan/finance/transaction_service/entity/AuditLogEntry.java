package com.suhasan.finance.transaction_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log_entries", indexes = {
        @Index(name = "idx_audit_created_at", columnList = "createdAt"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_outcome", columnList = "outcome"),
        @Index(name = "idx_audit_user_id", columnList = "userId"),
        @Index(name = "idx_audit_transaction_id", columnList = "transactionId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @Column(length = 36)
    private String eventId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 64)
    private String outcome;

    @Column(length = 128)
    private String userId;

    @Column(length = 36)
    private String transactionId;

    private String fromAccountId;

    private String toAccountId;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String details;

    @Column(length = 100)
    private String errorCode;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    void onCreate() {
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
