package com.suhasan.finance.transaction_service.ledger.domain;

public enum ReconciliationExceptionStatus {
    OPEN,
    ACKNOWLEDGED,
    IN_PROGRESS,
    RESOLVED,
    WAIVED
}
