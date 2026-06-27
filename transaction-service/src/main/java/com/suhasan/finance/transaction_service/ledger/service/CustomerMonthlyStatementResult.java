package com.suhasan.finance.transaction_service.ledger.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CustomerMonthlyStatementResult(
        UUID statementId,
        String ownerId,
        String externalAccountId,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        int statementVersion,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        LocalDateTime generatedAt,
        List<CustomerMonthlyStatementLineResult> lines) {
}
