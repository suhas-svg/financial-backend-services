package com.suhasan.finance.account_service.exception;

public class MfaVerificationException extends RuntimeException {
    public MfaVerificationException(String message) {
        super(message);
    }
}
