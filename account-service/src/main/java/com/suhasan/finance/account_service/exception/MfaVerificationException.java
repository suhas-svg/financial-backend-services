package com.suhasan.finance.account_service.exception;

public class MfaVerificationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MfaVerificationException(final String message) {
        super(message);
    }
}
