package com.suhasan.finance.transaction_service.entity;

public enum TransactionStatus {
    PENDING("Transaction is pending processing"),
    PROCESSING("Transaction is being processed"),
    COMPLETED("Transaction completed successfully"),
    FAILED("Transaction failed"),
    REVERSED("Transaction has been reversed"),
    CANCELLED("Transaction was cancelled");
    
    private final String description;
    
    TransactionStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}