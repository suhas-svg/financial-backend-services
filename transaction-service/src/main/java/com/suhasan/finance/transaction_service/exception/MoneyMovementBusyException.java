package com.suhasan.finance.transaction_service.exception;

/**
 * Every money-movement slot stayed busy for the configured wait. Nothing was claimed or
 * moved, so the client can retry the same request (same Idempotency-Key) shortly.
 */
public class MoneyMovementBusyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MoneyMovementBusyException() {
        super("The service is busy processing other payments. Nothing was charged; retry shortly.");
    }
}
