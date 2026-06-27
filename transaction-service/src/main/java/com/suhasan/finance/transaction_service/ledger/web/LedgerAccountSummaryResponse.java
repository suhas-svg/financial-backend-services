package com.suhasan.finance.transaction_service.ledger.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LedgerAccountSummaryResponse(
        String externalAccountId,
        String currency,
        BigDecimal postedBalance,
        BigDecimal pendingBalance,
        BigDecimal availableBalance,
        long projectionVersion,
        LocalDateTime updatedAt) {
}
