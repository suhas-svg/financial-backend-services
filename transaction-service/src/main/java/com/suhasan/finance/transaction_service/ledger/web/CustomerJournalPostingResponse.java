package com.suhasan.finance.transaction_service.ledger.web;

import java.math.BigDecimal;

public record CustomerJournalPostingResponse(
        String externalAccountId,
        String direction,
        BigDecimal amount,
        String currency,
        String memo) {
}
