package com.suhasan.finance.transaction_service.ledger.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CustomerStatementResponse(
        UUID statementId,
        String externalAccountId,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        int statementVersion,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        LocalDateTime generatedAt,
        List<CustomerStatementLineResponse> lines) {
}
