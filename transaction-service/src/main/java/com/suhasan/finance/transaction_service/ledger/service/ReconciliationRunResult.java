package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.ledger.domain.ReconciliationRunStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationRunResult(
        UUID runId,
        LocalDate businessDate,
        ReconciliationRunStatus status,
        int totalExceptions,
        int criticalExceptions) {
}
