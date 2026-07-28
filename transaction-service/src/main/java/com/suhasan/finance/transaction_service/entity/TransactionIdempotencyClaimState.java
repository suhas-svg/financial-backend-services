package com.suhasan.finance.transaction_service.entity;

public enum TransactionIdempotencyClaimState {
    CLAIMED,
    RESERVED,
    COMPLETED_PENDING_CONSUME,
    COMPLETED,
    RELEASED,
    RECONCILIATION_REQUIRED,
    CLOSED_NO_RESERVATION
}
