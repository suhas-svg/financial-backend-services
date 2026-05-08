package com.suhasan.finance.transaction_service.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogEntryResponse {
    private String eventId;
    private String eventType;
    private String action;
    private String outcome;
    private String userId;
    private String transactionId;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String currency;
    private String ipAddress;
    private String details;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private String metadata;
}
