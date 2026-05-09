package com.suhasan.finance.transaction_service.dto;

public record RiskCaseSummaryResponse(
        long totalCases,
        long openCases,
        long inReviewCases,
        long resolvedCases,
        long closedCases,
        long unassignedCases
) {
}
