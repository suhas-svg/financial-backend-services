package com.suhasan.finance.transaction_service.ledger.web;

public record ReconciliationExceptionStatusRequest(
        String status,
        String note,
        long expectedVersion) {
}
