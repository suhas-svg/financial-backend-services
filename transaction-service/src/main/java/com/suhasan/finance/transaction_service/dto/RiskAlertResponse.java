package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import com.suhasan.finance.transaction_service.entity.RiskAlertType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RiskAlertResponse {
    private String alertId;
    private RiskAlertType alertType;
    private RiskAlertSeverity severity;
    private RiskAlertStatus status;
    private String userId;
    private String transactionId;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private String recommendation;
    private String dedupeKey;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String resolutionNote;
}
