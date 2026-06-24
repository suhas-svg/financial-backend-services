package com.suhasan.finance.transaction_service.ledger.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PostingDraft(
        UUID ledgerAccountId,
        PostingDirection direction,
        BigDecimal amount,
        String currency,
        String memo) {
}
