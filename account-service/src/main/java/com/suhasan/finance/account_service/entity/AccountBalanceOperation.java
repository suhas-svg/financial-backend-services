package com.suhasan.finance.account_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_balance_operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountBalanceOperation {

    @EmbeddedId
    private AccountBalanceOperationId id;

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal delta;

    @Column(length = 255)
    private String reason;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    @Column(nullable = false)
    private boolean applied;

    @Column(name = "resulting_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal resultingBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BalanceOperationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
