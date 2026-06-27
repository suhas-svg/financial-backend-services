package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.ledger.domain.JournalDraft;
import com.suhasan.finance.transaction_service.ledger.domain.JournalType;
import com.suhasan.finance.transaction_service.ledger.domain.PostingDraft;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JournalCommand(
        JournalType journalType,
        String currency,
        LocalDate effectiveDate,
        String description,
        String correlationId,
        String createdBy,
        String idempotencyScope,
        String idempotencyKey,
        String requestFingerprint,
        List<PostingDraft> postings,
        UUID reversalOfJournalId) {

    public JournalCommand(
            JournalType journalType,
            String currency,
            LocalDate effectiveDate,
            String description,
            String correlationId,
            String createdBy,
            String idempotencyScope,
            String idempotencyKey,
            String requestFingerprint,
            List<PostingDraft> postings) {
        this(journalType, currency, effectiveDate, description, correlationId, createdBy,
                idempotencyScope, idempotencyKey, requestFingerprint, postings, null);
    }

    public JournalCommand {
        if (journalType == null || effectiveDate == null) {
            throw new IllegalArgumentException("Journal type and effective date are required");
        }
        if (isBlank(correlationId) || isBlank(createdBy) || isBlank(idempotencyScope)
                || isBlank(idempotencyKey) || isBlank(requestFingerprint)) {
            throw new IllegalArgumentException("Journal identity and idempotency fields are required");
        }
        postings = new JournalDraft(currency, postings).postings();
    }

    public JournalDraft draft() {
        return new JournalDraft(currency, postings);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
