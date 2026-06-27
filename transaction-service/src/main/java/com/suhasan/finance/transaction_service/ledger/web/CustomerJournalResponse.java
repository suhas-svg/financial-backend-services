package com.suhasan.finance.transaction_service.ledger.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CustomerJournalResponse(
        UUID journalId,
        String journalReference,
        String journalType,
        String state,
        String currency,
        BigDecimal customerAmount,
        String description,
        LocalDateTime postedAt,
        UUID reversalOfJournalId,
        List<CustomerJournalPostingResponse> postings) {
}
