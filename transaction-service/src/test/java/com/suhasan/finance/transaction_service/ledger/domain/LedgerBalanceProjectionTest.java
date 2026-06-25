package com.suhasan.finance.transaction_service.ledger.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerBalanceProjectionTest {

    @Test
    void pendingDebitReducesAvailableWithoutChangingPostedBalance() {
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(
                UUID.randomUUID(), new BigDecimal("100.00"));

        projection.reserveDebit(new BigDecimal("25.00"), 1L);

        assertThat(projection.getPostedBalance()).isEqualByComparingTo("100.00");
        assertThat(projection.getPendingBalance()).isEqualByComparingTo("-25.00");
        assertThat(projection.getPendingDebits()).isEqualByComparingTo("25.00");
        assertThat(projection.getAvailableBalance()).isEqualByComparingTo("75.00");
    }

    @Test
    void pendingCreditIsVisibleButUnavailableUntilPosted() {
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(
                UUID.randomUUID(), new BigDecimal("100.00"));

        projection.reserveCredit(new BigDecimal("40.00"), 1L);

        assertThat(projection.getPendingBalance()).isEqualByComparingTo("40.00");
        assertThat(projection.getPendingCredits()).isEqualByComparingTo("40.00");
        assertThat(projection.getAvailableBalance()).isEqualByComparingTo("100.00");

        projection.postPendingCredit(new BigDecimal("40.00"), 2L);

        assertThat(projection.getPostedBalance()).isEqualByComparingTo("140.00");
        assertThat(projection.getPendingBalance()).isZero();
        assertThat(projection.getPendingCredits()).isZero();
        assertThat(projection.getAvailableBalance()).isEqualByComparingTo("140.00");
    }

    @Test
    void postingPendingDebitMovesReservationIntoPostedBalance() {
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(
                UUID.randomUUID(), new BigDecimal("100.00"));
        projection.reserveDebit(new BigDecimal("25.00"), 1L);

        projection.postPendingDebit(new BigDecimal("25.00"), 2L);

        assertThat(projection.getPostedBalance()).isEqualByComparingTo("75.00");
        assertThat(projection.getPendingBalance()).isZero();
        assertThat(projection.getPendingDebits()).isZero();
        assertThat(projection.getAvailableBalance()).isEqualByComparingTo("75.00");
    }

    @Test
    void failingPendingDebitReleasesAvailability() {
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(
                UUID.randomUUID(), new BigDecimal("100.00"));
        projection.reserveDebit(new BigDecimal("25.00"), 1L);

        projection.releasePendingDebit(new BigDecimal("25.00"), 2L);

        assertThat(projection.getPostedBalance()).isEqualByComparingTo("100.00");
        assertThat(projection.getPendingBalance()).isZero();
        assertThat(projection.getAvailableBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsOverspendingAndOutOfOrderEvents() {
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(
                UUID.randomUUID(), new BigDecimal("20.00"));

        assertThatThrownBy(() -> projection.reserveDebit(new BigDecimal("25.00"), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient available balance");

        projection.reserveDebit(new BigDecimal("5.00"), 2L);
        assertThatThrownBy(() -> projection.releasePendingDebit(new BigDecimal("5.00"), 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event sequence");
    }

    @Test
    void systemAccountDebitCanReserveBeyondAvailableBalance() {
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(
                UUID.randomUUID(), BigDecimal.ZERO);

        projection.reserveDebitAllowNegative(new BigDecimal("25.00"), 1L);

        assertThat(projection.getPendingDebits()).isEqualByComparingTo("25.00");
        assertThat(projection.getAvailableBalance()).isEqualByComparingTo("-25.00");
    }
}
