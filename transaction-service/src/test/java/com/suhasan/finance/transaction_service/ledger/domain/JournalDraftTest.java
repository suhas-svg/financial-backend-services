package com.suhasan.finance.transaction_service.ledger.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalDraftTest {

    @Test
    void acceptsBalancedSingleCurrencyPostings() {
        JournalDraft draft = new JournalDraft("USD", List.of(
                posting(PostingDirection.DEBIT, "10.00", "USD"),
                posting(PostingDirection.CREDIT, "10.00", "USD")));

        assertThat(draft.debitTotal()).isEqualByComparingTo("10.00");
        assertThat(draft.creditTotal()).isEqualByComparingTo("10.00");
    }

    @Test
    void rejectsUnbalancedPostings() {
        assertThatThrownBy(() -> new JournalDraft("USD", List.of(
                posting(PostingDirection.DEBIT, "10.00", "USD"),
                posting(PostingDirection.CREDIT, "9.00", "USD"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("balanced");
    }

    @Test
    void rejectsMixedCurrencyAndNonPositiveAmounts() {
        assertThatThrownBy(() -> new JournalDraft("USD", List.of(
                posting(PostingDirection.DEBIT, "10.00", "USD"),
                posting(PostingDirection.CREDIT, "10.00", "EUR"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");

        assertThatThrownBy(() -> new JournalDraft("USD", List.of(
                posting(PostingDirection.DEBIT, "0.00", "USD"),
                posting(PostingDirection.CREDIT, "0.00", "USD"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    private PostingDraft posting(PostingDirection direction, String amount, String currency) {
        return new PostingDraft(UUID.randomUUID(), direction, new BigDecimal(amount), currency, "test");
    }
}
