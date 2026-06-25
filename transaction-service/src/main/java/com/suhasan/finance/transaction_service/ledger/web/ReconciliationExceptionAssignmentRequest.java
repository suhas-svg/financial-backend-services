package com.suhasan.finance.transaction_service.ledger.web;

public record ReconciliationExceptionAssignmentRequest(
        String assignedTo,
        long expectedVersion) {
}
