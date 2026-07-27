package com.suhasan.finance.transaction_service.entity;

public enum TransactionIdempotencyClaimState {
    CLAIMED,
    RESERVED,
    COMPLETED,
    RELEASED,
    RECONCILIATION_REQUIRED,
    CLOSED_NO_RESERVATION
}
