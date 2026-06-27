package com.suhasan.finance.transaction_service.ledger.web;

public class LedgerAccountNotFoundException extends RuntimeException {
    public LedgerAccountNotFoundException(String message) {
        super(message);
    }
}
