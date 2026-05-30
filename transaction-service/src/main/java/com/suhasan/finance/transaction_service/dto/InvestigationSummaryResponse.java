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
    private long disputes;
    private long disputeNotes;
    private long failures;
    private long reversals;
    private long highSeverityItems;

    public InvestigationSummaryResponse(long transactions, long auditEvents, long riskAlerts, long riskCases,
                                        long failures, long reversals, long highSeverityItems) {
        this(transactions, auditEvents, riskAlerts, riskCases, 0, 0, failures, reversals, highSeverityItems);
    }
}
