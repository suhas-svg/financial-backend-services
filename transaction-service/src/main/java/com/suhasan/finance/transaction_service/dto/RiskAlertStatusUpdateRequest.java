package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import lombok.Data;

@Data
public class RiskAlertStatusUpdateRequest {
    private RiskAlertStatus status;
    private String resolutionNote;
}
