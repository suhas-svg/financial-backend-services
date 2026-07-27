package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.SpendingLimitDtos;
import com.suhasan.finance.account_service.entity.AccountSpendingLimit;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.entity.MfaMethod;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingLimitServiceTest {
    @Mock AccountRepository accounts;
    @Mock AccountSpendingLimitRepository limits;
    @Mock SpendingLimitReservationRepository reservations;
    @Mock SpendingLimitAuditEventRepository audits;
    @Mock MfaService mfa;
    @Mock NotificationService notifications;

    SpendingLimitService service;

    @BeforeEach
    void setUp() {
        service = new SpendingLimitService(accounts, limits, reservations, audits, mfa, notifications);
        when(audits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reservesUsageOnceAndRejectsAmountAboveRemainingDailyLimit() {
        CheckingAccount account = account("alice", "USD");
        AccountSpendingLimit limit = limit("alice");
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(limits.lock(7L)).thenReturn(Optional.of(limit));
        when(reservations.findByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(7L, "key-1"))
                .thenReturn(List.of());
        when(reservations.used(7L, "TRANSFER", LocalDate.now())).thenReturn(new BigDecimal("80.00"));

        SpendingLimitDtos.ReserveResponse response = service.reserve(7L,
                new SpendingLimitDtos.ReserveRequest(
                        "TRANSFER", new BigDecimal("25.00"), "key-1", "alice", "USD", "tx-claim-1"));

        assertThat(response.allowed()).isFalse();
        assertThat(response.remaining()).isEqualByComparingTo("20.00");
        verify(reservations, never()).saveAndFlush(any());
        verify(notifications).createInternal(any());
    }

    @Test
    void sameKeyAndSamePayloadReturnsOriginalReservation() {
        CheckingAccount account = account("alice", "USD");
        AccountSpendingLimit limit = limit("alice");
        SpendingLimitReservation original = reservation("TRANSFER", "alice", "USD", "25.00", "key-1");
        original.setReservationId(44L);
        original.setFingerprint(fingerprintForOriginal(service, account, original));
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(limits.lock(7L)).thenReturn(Optional.of(limit));
        when(reservations.findByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(7L, "key-1"))
                .thenReturn(List.of(original));
        when(reservations.used(7L, "TRANSFER", LocalDate.now())).thenReturn(new BigDecimal("25.00"));

        SpendingLimitDtos.ReserveResponse response = service.reserve(7L,
                new SpendingLimitDtos.ReserveRequest(
                        "TRANSFER", new BigDecimal("25.0"), "key-1", "alice", "USD", "tx-claim-1"));

        assertThat(response.allowed()).isTrue();
        assertThat(response.replay()).isTrue();
        assertThat(response.reservationId()).isEqualTo(44L);
        assertThat(response.amount()).isEqualByComparingTo("25.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.fingerprint()).isEqualTo(original.getFingerprint());
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void sameKeyAndDifferentAmountConflicts() {
        assertPayloadConflict(new SpendingLimitDtos.ReserveRequest(
                "TRANSFER", new BigDecimal("30.00"), "key-1", "alice", "USD", "tx-claim-1"));
    }

    @Test
    void sameKeyAndDifferentCurrencyUserOrOperationConflicts() {
        assertPayloadConflict(new SpendingLimitDtos.ReserveRequest(
                "TRANSFER", new BigDecimal("25.00"), "key-1", "alice", "EUR", "tx-claim-1"));
        assertPayloadConflict(new SpendingLimitDtos.ReserveRequest(
                "TRANSFER", new BigDecimal("25.00"), "key-1", "bob", "USD", "tx-claim-1"));
        assertPayloadConflict(new SpendingLimitDtos.ReserveRequest(
                "WITHDRAWAL", new BigDecimal("25.00"), "key-1", "alice", "USD", "tx-claim-1"));
    }

    @Test
    void consumesAndReleasesByReservationIdentityWithoutDeletingHistory() {
        CheckingAccount account = account("alice", "USD");
        AccountSpendingLimit limit = limit("alice");
        SpendingLimitReservation original = reservation("WITHDRAWAL", "alice", "USD", "25.00", "key-1");
        original.setReservationId(55L);
        original.setFingerprint(fingerprintForOriginal(service, account, original));
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(limits.lock(7L)).thenReturn(Optional.of(limit));
        when(reservations.lockByReservationIdAndAccountId(55L, 7L)).thenReturn(Optional.of(original));
        when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservations.used(7L, "WITHDRAWAL", LocalDate.now())).thenReturn(new BigDecimal("25.00"));

        SpendingLimitDtos.ReserveResponse consumed = service.consume(7L, 55L,
                new SpendingLimitDtos.ReservationTransitionRequest("alice", "tx-claim-1", "TRANSACTION_COMPLETED"));

        assertThat(consumed.state()).isEqualTo("CONSUMED");
        assertThat(original.getOutcome()).isEqualTo("TRANSACTION_COMPLETED");
        assertThatThrownBy(() -> service.release(7L, 55L,
                new SpendingLimitDtos.ReservationTransitionRequest("alice", "tx-claim-1", "FAILED")))
                .isInstanceOf(IllegalStateException.class);
        verify(reservations, never()).delete(any());
    }

    @Test
    void leaseExpiryPreservesCorrelatedReservationForReconciliation() {
        SpendingLimitReservation reservation = reservation("TRANSFER", "alice", "USD", "25.00", "key-1");
        reservation.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(reservations.findTop100ByStateAndExpiresAtBeforeOrderByExpiresAtAsc(
                org.mockito.ArgumentMatchers.eq(SpendingLimitReservationState.RESERVED), any()))
                .thenReturn(List.of(reservation));
        when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reconcileExpiredLeases();

        assertThat(reservation.getState()).isEqualTo(SpendingLimitReservationState.RECONCILIATION_REQUIRED);
        assertThat(reservation.getOutcome()).isEqualTo("LEASE_EXPIRED_PENDING_TRANSACTION_RECONCILIATION");
    }

    @Test
    void mixedUpdateAppliesReductionImmediatelyAndCoolsOnlyIncrease() {
        CheckingAccount account = account("alice", "USD");
        AccountSpendingLimit limit = limit("alice");
        MfaMethod method = new MfaMethod();
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(limits.lock(7L)).thenReturn(Optional.of(limit));
        when(mfa.activeMethod("alice")).thenReturn(method);
        when(mfa.verifyCredential(method, "123456")).thenReturn(true);
        when(limits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpendingLimitDtos.LimitResponse response = service.update(7L, "alice",
                new SpendingLimitDtos.UpdateRequest(
                        new BigDecimal("80.00"), new BigDecimal("75.00"), "123456"));

        assertThat(response.transferDailyLimit()).isEqualByComparingTo("80.00");
        assertThat(response.withdrawalDailyLimit()).isEqualByComparingTo("50.00");
        assertThat(response.pendingTransferDailyLimit()).isEqualByComparingTo("80.00");
        assertThat(response.pendingWithdrawalDailyLimit()).isEqualByComparingTo("75.00");
        assertThat(response.pendingEffectiveAt()).isAfter(LocalDateTime.now().plusHours(23));
    }

    private void assertPayloadConflict(SpendingLimitDtos.ReserveRequest replay) {
        CheckingAccount account = account("alice", "USD");
        SpendingLimitReservation original = reservation("TRANSFER", "alice", "USD", "25.00", "key-1");
        original.setFingerprint(fingerprintForOriginal(service, account, original));
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(reservations.findByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(7L, "key-1"))
                .thenReturn(List.of(original));

        assertThatThrownBy(() -> service.reserve(7L, replay))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different spending-limit reservation payload");
    }

    private CheckingAccount account(String owner, String currency) {
        CheckingAccount account = new CheckingAccount();
        account.setId(7L);
        account.setOwnerId(owner);
        account.setCurrency(currency);
        return account;
    }

    private AccountSpendingLimit limit(String owner) {
        AccountSpendingLimit limit = new AccountSpendingLimit();
        limit.setAccountId(7L);
        limit.setTransferDailyLimit(new BigDecimal("100.00"));
        limit.setWithdrawalDailyLimit(new BigDecimal("50.00"));
        limit.setUpdatedAt(LocalDateTime.now());
        limit.setUpdatedBy(owner);
        return limit;
    }

    private SpendingLimitReservation reservation(String operation, String owner, String currency,
                                                  String amount, String key) {
        LocalDateTime now = LocalDateTime.now();
        return SpendingLimitReservation.builder()
                .accountId(7L)
                .ownerId(owner)
                .operationType(operation)
                .amount(new BigDecimal(amount))
                .currency(currency)
                .usageDate(LocalDate.now())
                .idempotencyKey(key)
                .transactionCorrelation("tx-claim-1")
                .state(SpendingLimitReservationState.RESERVED)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusMinutes(30))
                .build();
    }

    private String fingerprintForOriginal(SpendingLimitService ignored, CheckingAccount account,
                                          SpendingLimitReservation reservation) {
        String canonical = String.join("|", String.valueOf(account.getId()), reservation.getOwnerId(),
                reservation.getOperationType(), reservation.getAmount().stripTrailingZeros().toPlainString(),
                reservation.getCurrency(), reservation.getIdempotencyKey());
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
