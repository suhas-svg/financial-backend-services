package com.suhasan.finance.transaction_service.entity;

public enum TransactionProcessingState {
    INITIATED("Transaction has been created and processing started"),
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
