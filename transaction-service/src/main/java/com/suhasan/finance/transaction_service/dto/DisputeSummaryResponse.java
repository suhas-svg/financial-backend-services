package com.suhasan.finance.transaction_service.dto;

public record DisputeSummaryResponse(
        long totalDisputes,
        long openDisputes,
        long inReviewDisputes,
        long approvedDisputes,
        long deniedDisputes,
        long closedDisputes,
        long unassignedDisputes
) {
}
