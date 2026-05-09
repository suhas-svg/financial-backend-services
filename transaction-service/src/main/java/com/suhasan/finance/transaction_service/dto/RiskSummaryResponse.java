package com.suhasan.finance.transaction_service.dto;

public record RiskSummaryResponse(
        long totalAlerts,
        long openAlerts,
        long highSeverityAlerts,
        long escalatedAlerts
) {
}
