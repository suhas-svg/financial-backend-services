package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import com.suhasan.finance.transaction_service.entity.RiskAlertType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RiskAlertFilter {
    private RiskAlertStatus status;
    private RiskAlertSeverity severity;
    private RiskAlertType alertType;
    private String userId;
    private String transactionId;
    private LocalDateTime from;
    private LocalDateTime to;
}
