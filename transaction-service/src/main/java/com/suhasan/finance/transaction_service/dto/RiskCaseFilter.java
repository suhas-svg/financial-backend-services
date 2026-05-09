package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.RiskCasePriority;
import com.suhasan.finance.transaction_service.entity.RiskCaseStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RiskCaseFilter {
    private RiskCaseStatus status;
    private RiskCasePriority priority;
    private String assignedTo;
    private String userId;
    private String transactionId;
    private String alertId;
    private LocalDateTime from;
    private LocalDateTime to;
}
