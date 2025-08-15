package com.suhasan.finance.transaction_service.exception;

public class TransactionLimitExceededException extends RuntimeException {
    public TransactionLimitExceededException(String message) {
        super(message);
    }
    
    public TransactionLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}