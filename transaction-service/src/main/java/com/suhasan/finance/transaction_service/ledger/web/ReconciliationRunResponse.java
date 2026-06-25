package com.suhasan.finance.transaction_service.ledger.web;

import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationRunResponse(
        UUID runId,
        LocalDate businessDate,
        String reconciliationType,
        String status,
        int totalExceptions,
        int criticalExceptions) {
}
