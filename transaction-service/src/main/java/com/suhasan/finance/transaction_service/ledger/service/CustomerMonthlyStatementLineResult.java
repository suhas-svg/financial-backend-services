package com.suhasan.finance.transaction_service.ledger.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerMonthlyStatementLineResult(
        UUID lineId,
        UUID journalId,
        int lineSequence,
        LocalDate effectiveDate,
        String description,
        BigDecimal amount,
        BigDecimal runningBalance,
        String currency) {
}
