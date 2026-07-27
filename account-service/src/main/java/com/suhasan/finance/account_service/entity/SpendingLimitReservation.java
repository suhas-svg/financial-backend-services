package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "spending_limit_reservations", indexes = {
        @Index(name = "idx_limit_reservation_key", columnList = "accountId,idempotencyKey"),
        @Index(name = "idx_limit_reservation_correlation", columnList = "transactionCorrelation"),
        @Index(name = "idx_limit_reservation_reconciliation", columnList = "state,expiresAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingLimitReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reservationId;

    @Column(nullable = false)
    private Long accountId;

    @Column(length = 100)
    private String ownerId;

    @Column(nullable = false, length = 20)
    private String operationType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false, length = 160)
    private String idempotencyKey;

    @Column(length = 64)
    private String fingerprint;

    @Column(length = 256, unique = true)
    private String requestScope;

    @Column(length = 160)
    private String transactionCorrelation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private SpendingLimitReservationState state = SpendingLimitReservationState.RESERVED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;

    @Column(length = 120)
    private String outcome;

    private LocalDateTime outcomeAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (state == null) {
            state = SpendingLimitReservationState.RESERVED;
        }
        ensureRequestScope();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        ensureRequestScope();
    }

    private void ensureRequestScope() {
        if ((requestScope == null || requestScope.isBlank())
                && accountId != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            requestScope = accountId + "|" + idempotencyKey;
        }
    }
}
