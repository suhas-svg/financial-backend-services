package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionFilterRequest {
    
    private String accountId;
    private TransactionType type;
    private TransactionStatus status;
    
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDate;
    
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endDate;
    
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    
    private String description;
    private String reference;
    
    // For searching by account (either from or to)
    private String fromAccountId;
    private String toAccountId;
    
    // For user-specific searches
    private String createdBy;
}