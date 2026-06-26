package com.suhasan.finance.transaction_service.ledger.web;

public record CustomerStatementGenerateRequest(
        String externalAccountId,
        String yearMonth) {
}
