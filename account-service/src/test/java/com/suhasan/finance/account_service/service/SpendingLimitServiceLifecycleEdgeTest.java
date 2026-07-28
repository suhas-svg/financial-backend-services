package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.SpendingLimitDtos;
import com.suhasan.finance.account_service.entity.AccountSpendingLimit;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.entity.SpendingLimitReservation;
import com.suhasan.finance.account_service.entity.SpendingLimitReservationState;
import com.suhasan.finance.account_service.repository.AccountRepository;
import com.suhasan.finance.account_service.repository.AccountSpendingLimitRepository;
import com.suhasan.finance.account_service.repository.SpendingLimitAuditEventRepository;
import com.suhasan.finance.account_service.repository.SpendingLimitReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingLimitServiceLifecycleEdgeTest {
    @Mock AccountRepository accounts;
    @Mock AccountSpendingLimitRepository limits;
    @Mock SpendingLimitReservationRepository reservations;
    @Mock SpendingLimitAuditEventRepository audits;
    @Mock MfaService mfa;
    @Mock NotificationService notifications;

    private SpendingLimitService service;

    @BeforeEach
    void setUp() {
        service = new SpendingLimitService(accounts, limits, reservations, audits, mfa, notifications);
        lenient().when(audits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void lookupRejectsOperationAndOwnerMismatchesIndependently() {
        CheckingAccount account = account();
        SpendingLimitReservation reservation = reservation(SpendingLimitReservationState.RESERVED, "tx-1");
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(reservations.findFirstByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(7L, "key-1"))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.lookup(7L, "WITHDRAWAL", "key-1", "alice"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lookup payload");
        assertThatThrownBy(() -> service.lookup(7L, "TRANSFER", "key-1", "bob"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lookup payload");
    }

    @Test
    void transitionRejectsOwnerAndCorrelationMismatchesIndependently() {
        CheckingAccount account = account();
        SpendingLimitReservation reservation = reservation(SpendingLimitReservationState.RESERVED, "tx-1");
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(reservations.lockByReservationIdAndAccountId(44L, 7L))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.consume(7L, 44L,
                new SpendingLimitDtos.ReservationTransitionRequest("bob", "tx-1", "COMPLETED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner");
        assertThatThrownBy(() -> service.consume(7L, 44L,
                new SpendingLimitDtos.ReservationTransitionRequest("alice", "tx-2", "COMPLETED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("correlation");
    }

    @Test
    void consumingReleasedReservationMarksReconciliationBeforeRejecting() {
        CheckingAccount account = account();
        SpendingLimitReservation reservation = reservation(SpendingLimitReservationState.RELEASED, "tx-1");
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(reservations.lockByReservationIdAndAccountId(44L, 7L))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.consume(7L, 44L,
                new SpendingLimitDtos.ReservationTransitionRequest("alice", "tx-1", "COMPLETED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be consumed");

        assertThat(reservation.getState()).isEqualTo(SpendingLimitReservationState.RECONCILIATION_REQUIRED);
        assertThat(reservation.getOutcome())
                .isEqualTo("COMPLETED_TRANSACTION_FOUND_AFTER_RESERVATION_RELEASE");
        verify(reservations).save(reservation);
    }

    @Test
    void repeatingCompletedTransitionWithNoCorrelationIsIdempotent() {
        CheckingAccount account = account();
        AccountSpendingLimit limit = limit();
        SpendingLimitReservation reservation = reservation(SpendingLimitReservationState.CONSUMED, "tx-1");
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(reservations.lockByReservationIdAndAccountId(44L, 7L))
                .thenReturn(Optional.of(reservation));
        when(limits.lock(7L)).thenReturn(Optional.of(limit));
        when(reservations.used(eq(7L), eq("TRANSFER"), any(LocalDate.class)))
                .thenReturn(new BigDecimal("25.00"));

        SpendingLimitDtos.ReserveResponse response = service.consume(7L, 44L,
                new SpendingLimitDtos.ReservationTransitionRequest("alice", null, null));

        assertThat(response.state()).isEqualTo("CONSUMED");
        assertThat(response.replay()).isTrue();
    }

    @Test
    void uncorrelatedExpiredLeasesCloseWithoutReconciliation() {
        SpendingLimitReservation missingCorrelation = reservation(
                SpendingLimitReservationState.RESERVED, null);
        SpendingLimitReservation blankCorrelation = reservation(
                SpendingLimitReservationState.RESERVED, " ");
        when(reservations.findTop100ByStateAndExpiresAtBeforeOrderByExpiresAtAsc(
                eq(SpendingLimitReservationState.RESERVED), any(LocalDateTime.class)))
                .thenReturn(List.of(missingCorrelation, blankCorrelation));

        service.reconcileExpiredLeases();

        assertThat(missingCorrelation.getState()).isEqualTo(SpendingLimitReservationState.EXPIRED);
        assertThat(blankCorrelation.getState()).isEqualTo(SpendingLimitReservationState.EXPIRED);
        assertThat(missingCorrelation.getOutcome())
                .isEqualTo("UNREFERENCED_RESERVATION_LEASE_EXPIRED");
    }

    @Test
    void legacyReleaseIsIdempotentAfterReservationWasAlreadyReleased() {
        CheckingAccount account = account();
        SpendingLimitReservation reservation = reservation(SpendingLimitReservationState.RELEASED, "tx-1");
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(reservations.findFirstByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(7L, "key-1"))
                .thenReturn(Optional.of(reservation));

        assertThat(service.release(7L, "TRANSFER", "key-1", "alice")).isFalse();
    }

    private CheckingAccount account() {
        CheckingAccount account = new CheckingAccount();
        account.setId(7L);
        account.setOwnerId("alice");
        account.setCurrency("USD");
        return account;
    }

    private AccountSpendingLimit limit() {
        AccountSpendingLimit limit = new AccountSpendingLimit();
        limit.setAccountId(7L);
        limit.setTransferDailyLimit(new BigDecimal("100.00"));
        limit.setWithdrawalDailyLimit(new BigDecimal("50.00"));
        limit.setUpdatedAt(LocalDateTime.now());
        limit.setUpdatedBy("alice");
        return limit;
    }

    private SpendingLimitReservation reservation(SpendingLimitReservationState state, String correlation) {
        LocalDateTime now = LocalDateTime.now();
        return SpendingLimitReservation.builder()
                .reservationId(44L)
                .accountId(7L)
                .ownerId("alice")
                .operationType("TRANSFER")
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .usageDate(LocalDate.now())
                .idempotencyKey("key-1")
                .fingerprint("complete-fingerprint")
                .requestScope("7|key-1")
                .transactionCorrelation(correlation)
                .state(state)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusMinutes(30))
                .build();
    }
}
