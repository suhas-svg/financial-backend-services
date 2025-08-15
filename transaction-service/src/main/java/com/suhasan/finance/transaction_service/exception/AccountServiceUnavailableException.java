package com.suhasan.finance.transaction_service.exception;

/**
 * Exception thrown when the Account Service is unavailable or unresponsive
 */
public class AccountServiceUnavailableException extends RuntimeException {
    
    public AccountServiceUnavailableException(String message) {
        super(message);
    }
    
    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}