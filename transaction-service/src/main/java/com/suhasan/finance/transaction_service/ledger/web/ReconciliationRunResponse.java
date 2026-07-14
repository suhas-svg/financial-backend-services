package com.suhasan.finance.transaction_service.ledger.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReconciliationRunResponse(
        UUID runId,
        LocalDate businessDate,
        String reconciliationType,
        String status,
        String requestedBy,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        int totalExceptions,
        int criticalExceptions) {
    public ReconciliationRunResponse(
            UUID runId,
            LocalDate businessDate,
            String reconciliationType,
            String status,
            int totalExceptions,
            int criticalExceptions) {
        this(runId, businessDate, reconciliationType, status, null, null, null, totalExceptions, criticalExceptions);
    }
}
