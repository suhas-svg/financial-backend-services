package com.suhasan.finance.transaction_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "risk_alerts", indexes = {
        @Index(name = "idx_risk_alert_created_at", columnList = "createdAt"),
        @Index(name = "idx_risk_alert_status", columnList = "status"),
        @Index(name = "idx_risk_alert_severity", columnList = "severity"),
        @Index(name = "idx_risk_alert_type", columnList = "alertType"),
        @Index(name = "idx_risk_alert_user_id", columnList = "userId"),
        @Index(name = "idx_risk_alert_transaction_id", columnList = "transactionId"),
        @Index(name = "idx_risk_alert_dedupe_key", columnList = "dedupeKey")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskAlert {

    @Id
    @Column(length = 36)
    private String alertId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private RiskAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RiskAlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RiskAlertStatus status;

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

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(length = 500)
    private String recommendation;

    @Column(nullable = false, length = 255, unique = true)
    private String dedupeKey;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 128)
    private String reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(length = 500)
    private String resolutionNote;

    @PrePersist
    void onCreate() {
        if (alertId == null) {
            alertId = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = RiskAlertStatus.OPEN;
        }
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
