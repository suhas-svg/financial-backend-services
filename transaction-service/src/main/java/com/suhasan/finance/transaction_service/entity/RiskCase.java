package com.suhasan.finance.transaction_service.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "risk_cases", indexes = {
        @Index(name = "idx_risk_case_number", columnList = "caseNumber"),
        @Index(name = "idx_risk_case_status", columnList = "status"),
        @Index(name = "idx_risk_case_priority", columnList = "priority"),
        @Index(name = "idx_risk_case_user_id", columnList = "userId"),
        @Index(name = "idx_risk_case_transaction_id", columnList = "transactionId"),
        @Index(name = "idx_risk_case_primary_alert_id", columnList = "primaryAlertId"),
        @Index(name = "idx_risk_case_assigned_to", columnList = "assignedTo"),
        @Index(name = "idx_risk_case_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskCase {

    @Id
    @Column(length = 36)
    private String caseId;

    @Column(nullable = false, unique = true, length = 32)
    private String caseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RiskCaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RiskCasePriority priority;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 128)
    private String userId;

    @Column(length = 36)
    private String transactionId;

    @Column(length = 36)
    private String primaryAlertId;

    @Column(length = 128)
    private String assignedTo;

    @Column(nullable = false, length = 128)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime claimedAt;

    private LocalDateTime closedAt;

    @Column(length = 500)
    private String resolutionNote;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "risk_case_alerts",
            joinColumns = @JoinColumn(name = "case_id"),
            inverseJoinColumns = @JoinColumn(name = "alert_id")
    )
    private List<RiskAlert> linkedAlerts = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "riskCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RiskCaseNote> notes = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (caseId == null) {
            caseId = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = RiskCaseStatus.OPEN;
        }
        if (priority == null) {
            priority = RiskCasePriority.LOW;
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
