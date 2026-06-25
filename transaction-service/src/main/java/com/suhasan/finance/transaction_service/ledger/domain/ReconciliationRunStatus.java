package com.suhasan.finance.transaction_service.ledger.domain;

public enum ReconciliationRunStatus {
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_EXCEPTIONS,
    FAILED
}
