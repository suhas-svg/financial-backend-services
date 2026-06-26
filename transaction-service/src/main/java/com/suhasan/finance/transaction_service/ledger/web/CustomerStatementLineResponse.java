package com.suhasan.finance.transaction_service.ledger.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerStatementLineResponse(
        UUID lineId,
        UUID journalId,
        int lineSequence,
        LocalDate effectiveDate,
        String description,
        BigDecimal amount,
        BigDecimal runningBalance,
        String currency) {
}
