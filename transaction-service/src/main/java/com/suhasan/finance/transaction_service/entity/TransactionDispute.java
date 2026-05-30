package com.suhasan.finance.transaction_service.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "transaction_disputes", indexes = {
        @Index(name = "idx_dispute_number", columnList = "disputeNumber"),
        @Index(name = "idx_dispute_status", columnList = "status"),
        @Index(name = "idx_dispute_user_id", columnList = "userId"),
        @Index(name = "idx_dispute_transaction_id", columnList = "transactionId"),
        @Index(name = "idx_dispute_assigned_to", columnList = "assignedTo"),
        @Index(name = "idx_dispute_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDispute {

    @Id
    @Column(length = 36)
    private String disputeId;

    @Column(nullable = false, unique = true, length = 32)
    private String disputeNumber;

    @Column(nullable = false, length = 36)
    private String transactionId;

    @Column(nullable = false, length = 128)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DisputeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private DisputeReasonCode reasonCode;

    @Column(nullable = false, length = 1000)
    private String description;

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

    @Column(length = 1000)
    private String resolutionNote;

    @Builder.Default
    @OneToMany(mappedBy = "dispute", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionDisputeNote> notes = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (disputeId == null) {
            disputeId = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = DisputeStatus.OPEN;
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
