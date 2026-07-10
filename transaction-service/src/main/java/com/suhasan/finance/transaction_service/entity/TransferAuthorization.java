package com.suhasan.finance.transaction_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_authorizations", uniqueConstraints =
        @UniqueConstraint(name = "uk_transfer_authorization_idempotency", columnNames = {"user_id", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
public class TransferAuthorization {
    @Id
    @Column(name = "authorization_id", length = 36)
    private String authorizationId;
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(name = "action_fingerprint", nullable = false, length = 64)
    private String actionFingerprint;
    @Column(name = "challenge_id", nullable = false, length = 36)
    private String challengeId;
    @Column(name = "from_account_id", nullable = false, length = 64)
    private String fromAccountId;
    @Column(name = "to_account_id", nullable = false, length = 64)
    private String toAccountId;
    @Column(name = "beneficiary_id", length = 36)
    private String beneficiaryId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(length = 500)
    private String description;
    @Column(length = 100)
    private String reference;
    @Column(name = "reason_codes", nullable = false, length = 500)
    private String reasonCodes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferAuthorizationStatus status;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "authorized_at")
    private Instant authorizedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "executed_transaction_id", length = 36)
    private String executedTransactionId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    void onCreate() {
        if (authorizationId == null) authorizationId = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = TransferAuthorizationStatus.PENDING;
        if (version == null) version = 0L;
    }
}
