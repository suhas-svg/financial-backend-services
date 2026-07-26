package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.SpendingLimitDtos;
import com.suhasan.finance.account_service.entity.AccountSpendingLimit;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.entity.MfaMethod;
import com.suhasan.finance.account_service.entity.SpendingLimitAuditEvent;
import com.suhasan.finance.account_service.repository.AccountRepository;
import com.suhasan.finance.account_service.repository.AccountSpendingLimitRepository;
import com.suhasan.finance.account_service.repository.SpendingLimitAuditEventRepository;
import com.suhasan.finance.account_service.repository.SpendingLimitReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpendingLimitServiceAdditionalTest {
    private AccountRepository accounts;
    private AccountSpendingLimitRepository limits;
    private SpendingLimitReservationRepository reservations;
    private SpendingLimitAuditEventRepository audits;
    private MfaService mfa;
    private NotificationService notifications;
    private SpendingLimitService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        limits = mock(AccountSpendingLimitRepository.class);
        reservations = mock(SpendingLimitReservationRepository.class);
        audits = mock(SpendingLimitAuditEventRepository.class);
        mfa = mock(MfaService.class);
        notifications = mock(NotificationService.class);
        service = new SpendingLimitService(accounts, limits, reservations, audits, mfa, notifications);
        when(limits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(limits.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listScopesOwnershipAndUsesDefaultOrStoredLimits() {
        CheckingAccount owned = account("alice");
        owned.setId(1L);
        CheckingAccount other = account("bob");
        other.setId(2L);
        when(accounts.findAll()).thenReturn(List.of(owned, other));
        when(limits.findById(1L)).thenReturn(Optional.empty());
        when(reservations.used(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        assertThat(service.list("alice")).singleElement().satisfies(view -> {
            assertThat(view.transferDailyLimit()).isEqualByComparingTo("10000");
            assertThat(view.withdrawalDailyLimit()).isEqualByComparingTo("2000");
        });
    }

    @Test
    void updateRejectsMissingOwnershipAndInvalidMfaThenAppliesImmediateReduction() {
        assertThatThrownBy(() -> service.update(1L, "alice", update("10", "10", null)))
                .isInstanceOf(IllegalArgumentException.class);
        CheckingAccount other = account("bob");
        when(accounts.findById(1L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.update(1L, "alice", update("10", "10", null)))
                .isInstanceOf(AccessDeniedException.class);

        CheckingAccount owned = account("alice");
        when(accounts.findById(1L)).thenReturn(Optional.of(owned));
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(owned));
        AccountSpendingLimit limit = limit("100", "50");
        when(limits.lock(1L)).thenReturn(Optional.of(limit));
        assertThatThrownBy(() -> service.update(1L, "alice", update("150", "50", " ")))
                .isInstanceOf(IllegalArgumentException.class);

        SpendingLimitDtos.LimitResponse reduced = service.update(1L, "alice", update("80", "40", null));
        assertThat(reduced.transferDailyLimit()).isEqualByComparingTo("80");
        assertThat(reduced.pendingEffectiveAt()).isNull();
    }

    @Test
    void verifiedDualIncreaseCoolsAndAppliesWhenDue() {
        CheckingAccount owned = account("alice");
        when(accounts.findById(1L)).thenReturn(Optional.of(owned));
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(owned));
        AccountSpendingLimit limit = limit("100", "50");
        when(limits.lock(1L)).thenReturn(Optional.of(limit));
        MfaMethod method = new MfaMethod();
        when(mfa.activeMethod("alice")).thenReturn(method);
        when(mfa.verifyCredential(method, "123456")).thenReturn(true);
        SpendingLimitDtos.LimitResponse pending = service.update(
                1L, "alice", update("150", "75", "123456"));
        assertThat(pending.pendingTransferDailyLimit()).isEqualByComparingTo("150");
        assertThat(pending.pendingWithdrawalDailyLimit()).isEqualByComparingTo("75");

        limit.setPendingEffectiveAt(LocalDateTime.now().minusMinutes(1));
        SpendingLimitDtos.LimitResponse applied = service.update(
                1L, "alice", update("150", "75", "123456"));
        assertThat(applied.transferDailyLimit()).isEqualByComparingTo("150");
        verify(limits, org.mockito.Mockito.times(3)).save(limit);
    }

    @Test
    void reserveRejectsOwnershipAndTypeThenAllowsWithdrawalAndNearLimit() {
        assertThatThrownBy(() -> service.reserve(1L, reserve("TRANSFER", "1", "alice")))
                .isInstanceOf(IllegalArgumentException.class);
        CheckingAccount other = account("bob");
        when(accounts.findById(1L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.reserve(1L, reserve("TRANSFER", "1", "alice")))
                .isInstanceOf(AccessDeniedException.class);

        CheckingAccount owned = account("alice");
        when(accounts.findById(1L)).thenReturn(Optional.of(owned));
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(owned));
        when(limits.lock(1L)).thenReturn(Optional.of(limit("100", "50")));
        assertThatThrownBy(() -> service.reserve(1L, reserve("CARD", "1", "alice")))
                .isInstanceOf(IllegalArgumentException.class);

        when(reservations.used(1L, "WITHDRAWAL", LocalDate.now())).thenReturn(new BigDecimal("10"));
        assertThat(service.reserve(1L, reserve("withdrawal", "5", "alice")).allowed()).isTrue();
        when(reservations.used(1L, "TRANSFER", LocalDate.now())).thenReturn(new BigDecimal("79"));
        assertThat(service.reserve(1L, reserve("transfer", "2", "alice")).remaining())
                .isEqualByComparingTo("19");
        verify(notifications).createInternal(any());
    }

    @Test
    void releaseRejectsMissingAndOwnershipAndAuditEventsAreNewestFirst() {
        assertThatThrownBy(() -> service.release(1L, "transfer", "key", "alice"))
                .isInstanceOf(IllegalArgumentException.class);
        CheckingAccount other = account("bob");
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.release(1L, "transfer", "key", "alice"))
                .isInstanceOf(AccessDeniedException.class);

        SpendingLimitAuditEvent old = SpendingLimitAuditEvent.builder()
                .eventId(1L).createdAt(LocalDateTime.now().minusHours(1)).build();
        SpendingLimitAuditEvent newest = SpendingLimitAuditEvent.builder()
                .eventId(2L).createdAt(LocalDateTime.now()).build();
        when(audits.findAll()).thenReturn(List.of(old, newest));
        assertThat(service.auditEvents()).extracting(SpendingLimitDtos.AuditResponse::eventId)
                .containsExactly(2L, 1L);
    }

    private CheckingAccount account(String owner) {
        CheckingAccount account = new CheckingAccount();
        account.setOwnerId(owner);
        return account;
    }

    private AccountSpendingLimit limit(String transfer, String withdrawal) {
        AccountSpendingLimit limit = new AccountSpendingLimit();
        limit.setAccountId(1L);
        limit.setTransferDailyLimit(new BigDecimal(transfer));
        limit.setWithdrawalDailyLimit(new BigDecimal(withdrawal));
        limit.setUpdatedAt(LocalDateTime.now());
        limit.setUpdatedBy("alice");
        return limit;
    }

    private SpendingLimitDtos.UpdateRequest update(String transfer, String withdrawal, String credential) {
        return new SpendingLimitDtos.UpdateRequest(
                new BigDecimal(transfer), new BigDecimal(withdrawal), credential);
    }

    private SpendingLimitDtos.ReserveRequest reserve(String type, String amount, String user) {
        return new SpendingLimitDtos.ReserveRequest(type, new BigDecimal(amount), type + "-key", user);
    }
}
