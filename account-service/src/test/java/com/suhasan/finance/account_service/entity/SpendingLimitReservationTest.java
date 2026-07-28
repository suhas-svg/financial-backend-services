package com.suhasan.finance.account_service.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SpendingLimitReservationTest {

    @Test
    void prePersistDefaultsTimestampsStateAndRequestScope() {
        SpendingLimitReservation reservation = SpendingLimitReservation.builder()
                .accountId(7L)
                .idempotencyKey("key-1")
                .state(null)
                .build();

        reservation.onCreate();

        assertThat(reservation.getCreatedAt()).isNotNull();
        assertThat(reservation.getUpdatedAt()).isEqualTo(reservation.getCreatedAt());
        assertThat(reservation.getState()).isEqualTo(SpendingLimitReservationState.RESERVED);
        assertThat(reservation.getRequestScope()).isEqualTo("7|key-1");
    }

    @Test
    void prePersistPreservesExplicitLifecycleAndScopeFields() {
        LocalDateTime created = LocalDateTime.now().minusHours(2);
        LocalDateTime updated = created.plusHours(1);
        SpendingLimitReservation reservation = SpendingLimitReservation.builder()
                .accountId(7L)
                .idempotencyKey("key-1")
                .createdAt(created)
                .updatedAt(updated)
                .state(SpendingLimitReservationState.RELEASED)
                .requestScope("legacy-safe-scope")
                .build();

        reservation.onCreate();

        assertThat(reservation.getCreatedAt()).isEqualTo(created);
        assertThat(reservation.getUpdatedAt()).isEqualTo(updated);
        assertThat(reservation.getState()).isEqualTo(SpendingLimitReservationState.RELEASED);
        assertThat(reservation.getRequestScope()).isEqualTo("legacy-safe-scope");
    }

    @Test
    void prePersistDoesNotInventScopeWithoutCompleteSafeInputs() {
        SpendingLimitReservation missingAccount = SpendingLimitReservation.builder()
                .idempotencyKey("key-1")
                .build();
        SpendingLimitReservation missingKey = SpendingLimitReservation.builder()
                .accountId(7L)
                .requestScope(" ")
                .build();
        SpendingLimitReservation blankKey = SpendingLimitReservation.builder()
                .accountId(7L)
                .idempotencyKey(" ")
                .requestScope("")
                .build();

        missingAccount.onCreate();
        missingKey.onCreate();
        blankKey.onCreate();

        assertThat(missingAccount.getRequestScope()).isNull();
        assertThat(missingKey.getRequestScope()).isBlank();
        assertThat(blankKey.getRequestScope()).isBlank();
    }

    @Test
    void preUpdateRefreshesUpdatedTimestamp() {
        LocalDateTime original = LocalDateTime.now().minusDays(1);
        SpendingLimitReservation reservation = SpendingLimitReservation.builder()
                .updatedAt(original)
                .build();

        reservation.onUpdate();

        assertThat(reservation.getUpdatedAt()).isAfter(original);
    }
}
