package com.suhasan.finance.transaction_service.ledger.domain;

import java.math.BigDecimal;
import java.util.List;

public record JournalDraft(String currency, List<PostingDraft> postings) {

    public JournalDraft {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Journal currency must be a three-letter uppercase code");
        }
        if (postings == null || postings.size() < 2) {
            throw new IllegalArgumentException("A journal requires at least two postings");
        }
        postings = List.copyOf(postings);
        for (PostingDraft posting : postings) {
            if (posting.ledgerAccountId() == null || posting.direction() == null) {
                throw new IllegalArgumentException("Posting account and direction are required");
            }
            if (posting.amount() == null || posting.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Posting amount must be positive");
            }
            if (!currency.equals(posting.currency())) {
                throw new IllegalArgumentException("Every posting currency must match the journal currency");
            }
        }
        if (total(postings, PostingDirection.DEBIT).compareTo(total(postings, PostingDirection.CREDIT)) != 0) {
            throw new IllegalArgumentException("Journal postings must be balanced");
        }
    }

    public BigDecimal debitTotal() {
        return total(postings, PostingDirection.DEBIT);
    }

    public BigDecimal creditTotal() {
        return total(postings, PostingDirection.CREDIT);
    }

    private static BigDecimal total(List<PostingDraft> postings, PostingDirection direction) {
        return postings.stream()
                .filter(posting -> posting.direction() == direction)
                .map(PostingDraft::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
