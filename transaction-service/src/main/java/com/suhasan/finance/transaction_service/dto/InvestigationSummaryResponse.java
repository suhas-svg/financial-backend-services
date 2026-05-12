package com.suhasan.finance.transaction_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InvestigationSummaryResponse {
    private long transactions;
    private long auditEvents;
    private long riskAlerts;
    private long riskCases;
    private long failures;
    private long reversals;
    private long highSeverityItems;
}
