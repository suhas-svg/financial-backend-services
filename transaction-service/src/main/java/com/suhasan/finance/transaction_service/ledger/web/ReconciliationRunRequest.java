package com.suhasan.finance.transaction_service.ledger.web;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReconciliationRunRequest(
        @NotNull(message = "businessDate is required, formatted as yyyy-MM-dd")
        LocalDate businessDate) {
}
