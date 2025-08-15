package com.suhasan.finance.transaction_service.exception;

/**
 * Exception thrown when attempting to reverse a transaction that has already been reversed
 */
public class TransactionAlreadyReversedException extends RuntimeException {
    
    private final String transactionId;
    
    public TransactionAlreadyReversedException(String transactionId) {
        super("Transaction " + transactionId + " has already been reversed");
        this.transactionId = transactionId;
    }
    
    public TransactionAlreadyReversedException(String transactionId, String message) {
        super(message);
        this.transactionId = transactionId;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
}