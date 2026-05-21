package com.suhasan.finance.account_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_debit_holds")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDebitHold {

    @Id
    @Column(length = 100)
    private String holdId;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 100)
    private String transactionId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DebitHoldStatus status;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime capturedAt;
    private LocalDateTime releasedAt;

    @Column(length = 100)
    private String capturedByTransactionId;

    @Column(length = 100)
    private String releasedByTransactionId;

    @Column(length = 255)
    private String releaseReason;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
