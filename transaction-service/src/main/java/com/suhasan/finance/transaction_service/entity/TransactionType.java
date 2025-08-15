package com.suhasan.finance.transaction_service.entity;

public enum TransactionType {
    TRANSFER("Transfer between accounts"),
    DEPOSIT("Deposit to account"),
    WITHDRAWAL("Withdrawal from account"),
    FEE("Service fee"),
    INTEREST("Interest payment"),
    REVERSAL("Transaction reversal"),
    REFUND("Transaction refund");
    
    private final String description;
    
    TransactionType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}