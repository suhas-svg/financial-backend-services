package com.suhasan.finance.transaction_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_from_account", columnList = "fromAccountId"),
    @Index(name = "idx_to_account", columnList = "toAccountId"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_idempotency_scope", columnList = "createdBy,type,idempotencyKey")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class Transaction {
    
    @Id
    @Column(length = 36)
    private String transactionId;
    
    @Column(nullable = false)
    private String fromAccountId;
    
    @Column(nullable = false)
    private String toAccountId;
    
    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @NotBlank
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    @Builder.Default
    private TransactionProcessingState processingState = TransactionProcessingState.INITIATED;
    
    @Size(max = 500)
    @Column(columnDefinition = "VARCHAR(500)")
    private String description;
    
    @Size(max = 100)
    private String reference;

    @Size(max = 128)
    @Column(length = 128)
    private String idempotencyKey;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime processedAt;
    
    @Column(nullable = false)
    private String createdBy;
    
    private String processedBy;
    
    // Audit fields
    @Column(columnDefinition = "TEXT")
    private String auditTrail;
    
    @Size(max = 45)
    private String ipAddress;
    
    @Size(max = 500)
    private String userAgent;
    
    // Balance tracking for audit
    private BigDecimal fromAccountBalanceBefore;
    private BigDecimal fromAccountBalanceAfter;
    private BigDecimal toAccountBalanceBefore;
    private BigDecimal toAccountBalanceAfter;
    
    // Reversal tracking fields
    @Column(length = 36)
    private String originalTransactionId;  // For reversal transactions, points to original
    
    @Column(length = 36)
    private String reversalTransactionId;  // For original transactions, points to reversal
    
    private LocalDateTime reversedAt;      // When the transaction was reversed
    
    private String reversedBy;             // Who reversed the transaction
    
    @Size(max = 500)
    private String reversalReason;         // Reason for reversal
    
    @PrePersist
    protected void onCreate() {
        if (transactionId == null) {
            transactionId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        if (status == TransactionStatus.COMPLETED && processedAt == null) {
            processedAt = LocalDateTime.now();
        }
    }
}
