package com.suhasan.finance.account_service.exception;

/**
 * Raised when an authentication scope (username, client IP) is temporarily locked.
 * The message is deliberately the same for every scope so a lockout never reveals
 * whether a username exists.
 */
public class TooManyAttemptsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public TooManyAttemptsException(final long retryAfterSeconds) {
        super("Too many attempts. Try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
