package com.suhasan.finance.transaction_service.dto;

public record AuditSummaryResponse(
        long totalEvents,
        long failureEvents,
        long reversalEvents,
        long securityEvents
) {
}
