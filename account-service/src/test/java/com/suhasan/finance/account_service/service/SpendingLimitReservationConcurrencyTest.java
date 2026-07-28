package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.SpendingLimitDtos;
import com.suhasan.finance.account_service.entity.AccountSpendingLimit;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.entity.SpendingLimitReservation;
import com.suhasan.finance.account_service.repository.AccountRepository;
import com.suhasan.finance.account_service.repository.AccountSpendingLimitRepository;
import com.suhasan.finance.account_service.repository.SpendingLimitAuditEventRepository;
import com.suhasan.finance.account_service.repository.SpendingLimitReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingLimitReservationConcurrencyTest {
    @Mock AccountRepository accounts;
    @Mock AccountSpendingLimitRepository limits;
    @Mock SpendingLimitReservationRepository reservations;
    @Mock SpendingLimitAuditEventRepository audits;
    @Mock MfaService mfa;
    @Mock NotificationService notifications;

    @Test
    void concurrentSameKeyDifferentAmountProducesOneReservationAndOneConflict() throws Exception {
        SpendingLimitService service = new SpendingLimitService(
                accounts, limits, reservations, audits, mfa, notifications);
        CheckingAccount account = new CheckingAccount();
        account.setId(7L);
        account.setOwnerId("alice");
        account.setCurrency("USD");
        AccountSpendingLimit limit = new AccountSpendingLimit();
        limit.setAccountId(7L);
        limit.setTransferDailyLimit(new BigDecimal("100.00"));
        limit.setWithdrawalDailyLimit(new BigDecimal("50.00"));
        limit.setUpdatedAt(LocalDateTime.now());
        limit.setUpdatedBy("alice");

        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(limits.lock(7L)).thenReturn(Optional.of(limit));
        when(reservations.used(7L, "TRANSFER", LocalDate.now())).thenReturn(BigDecimal.ZERO);
        when(audits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AtomicReference<SpendingLimitReservation> winner = new AtomicReference<>();
        AtomicInteger initialReads = new AtomicInteger();
        CountDownLatch bothReadBeforeInsert = new CountDownLatch(2);
        when(reservations.findByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(7L, "key-race"))
                .thenAnswer(invocation -> {
                    if (initialReads.incrementAndGet() <= 2) {
                        bothReadBeforeInsert.countDown();
                        assertThat(bothReadBeforeInsert.await(5, TimeUnit.SECONDS)).isTrue();
                        return List.of();
                    }
                    return winner.get() == null ? List.of() : List.of(winner.get());
                });
        when(reservations.saveAndFlush(any(SpendingLimitReservation.class)))
                .thenAnswer(invocation -> {
                    SpendingLimitReservation candidate = invocation.getArgument(0);
                    candidate.setReservationId(44L);
                    if (winner.compareAndSet(null, candidate)) {
                        return candidate;
                    }
                    throw new DataIntegrityViolationException("unique reservation idempotency scope");
                });
        when(reservations.findFirstByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(7L, "key-race"))
                .thenAnswer(invocation -> Optional.ofNullable(winner.get()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> capture(() -> service.reserve(7L,
                    new SpendingLimitDtos.ReserveRequest("TRANSFER", new BigDecimal("25.00"),
                            "key-race", "alice", "USD", "claim-a"))));
            Future<Object> second = executor.submit(() -> capture(() -> service.reserve(7L,
                    new SpendingLimitDtos.ReserveRequest("TRANSFER", new BigDecimal("30.00"),
                            "key-race", "alice", "USD", "claim-b"))));

            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(firstResult, secondResult).stream()
                    .filter(SpendingLimitDtos.ReserveResponse.class::isInstance).count()).isEqualTo(1);
            assertThat(List.of(firstResult, secondResult).stream()
                    .filter(IllegalStateException.class::isInstance).count()).isEqualTo(1);
            assertThat(winner.get()).isNotNull();
            assertThat(List.of(new BigDecimal("25.00"), new BigDecimal("30.00")).stream()
                    .anyMatch(expected -> expected.compareTo(winner.get().getAmount()) == 0)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private Object capture(ThrowingSupplier supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get();
    }
}
