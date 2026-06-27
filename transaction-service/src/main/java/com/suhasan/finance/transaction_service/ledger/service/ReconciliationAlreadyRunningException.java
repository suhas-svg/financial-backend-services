package com.suhasan.finance.transaction_service.ledger.service;

public class ReconciliationAlreadyRunningException extends RuntimeException {
    public ReconciliationAlreadyRunningException(String message) {
        super(message);
    }
}
