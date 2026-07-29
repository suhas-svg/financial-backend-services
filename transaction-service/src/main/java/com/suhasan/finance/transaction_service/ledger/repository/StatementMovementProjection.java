package com.suhasan.finance.transaction_service.ledger.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface StatementMovementProjection {
    UUID getJournalId();
    LocalDate getEffectiveDate();
    String getDescription();
    BigDecimal getAmount();
}
