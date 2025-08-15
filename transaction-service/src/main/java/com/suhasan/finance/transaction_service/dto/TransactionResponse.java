package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {
    
    private String transactionId;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String currency;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private String reference;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String createdBy;
    
    // Reversal information
    private String originalTransactionId;  // For reversal transactions
    private String reversalTransactionId;  // For original transactions that have been reversed
    private LocalDateTime reversedAt;      // When the transaction was reversed
    private String reversedBy;             // Who reversed the transaction
    private String reversalReason;         // Reason for reversal
}