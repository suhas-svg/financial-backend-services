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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction_idempotency_claims",
        uniqueConstraints = @UniqueConstraint(name = "uk_transaction_idempotency_claim",
                columnNames = {"user_id", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_transaction_claim_reconciliation", columnList = "state,expires_at"),
                @Index(name = "idx_transaction_claim_reservation", columnList = "reservation_id"),
                @Index(name = "idx_transaction_claim_transaction", columnList = "transaction_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionIdempotencyClaim {
    @Id
    @Column(name = "claim_id", length = 36)
    private String claimId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(name = "operation_type", nullable = false, length = 20)
    private String operationType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private TransactionIdempotencyClaimState state = TransactionIdempotencyClaimState.CLAIMED;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "reservation_fingerprint", length = 64)
    private String reservationFingerprint;

    @Column(name = "reservation_amount", precision = 19, scale = 2)
    private BigDecimal reservationAmount;

    @Column(name = "reservation_currency", length = 3)
    private String reservationCurrency;

    @Column(name = "reservation_state", length = 40)
    private String reservationState;

    @Column(name = "transaction_id", length = 36)
    private String transactionId;

    @Column(name = "failure_details", length = 1000)
    private String failureDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (claimId == null) {
            claimId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (state == null) {
            state = TransactionIdempotencyClaimState.CLAIMED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
