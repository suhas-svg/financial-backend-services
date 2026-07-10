package com.suhasan.finance.transaction_service.entity;

public enum TransactionProcessingState {
    INITIATED("Transaction has been created and processing started"),
    AWAITING_AUTHORIZATION("Transaction is waiting for step-up authorization"),
    AUTHORIZED("Step-up authorization has been completed"),
    AUTHORIZATION_EXPIRED("Step-up authorization expired"),
    AUTHORIZATION_CANCELLED("Step-up authorization was cancelled"),
    HOLD_PLACED("Debit authorization hold has been placed"),
    HOLD_CAPTURED("Debit authorization hold has been captured"),
    HOLD_RELEASED("Debit authorization hold has been released"),
    DEBIT_APPLIED("Debit side of transaction has been applied"),
    CREDIT_APPLIED("Credit side of transaction has been applied"),
    COMPLETED("Transaction processing completed"),
    COMPENSATED("Compensation/rollback step completed"),
    MANUAL_ACTION_REQUIRED("Automatic compensation failed; manual action required");

    private final String description;

    TransactionProcessingState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
