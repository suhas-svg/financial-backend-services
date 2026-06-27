package com.suhasan.finance.transaction_service.ledger.web;

import java.time.LocalDate;

public record ReconciliationRunRequest(LocalDate businessDate) {
}
