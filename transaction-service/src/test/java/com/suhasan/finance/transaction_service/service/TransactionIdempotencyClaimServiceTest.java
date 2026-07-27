package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionIdempotencyClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionIdempotencyClaimServiceTest {
    @Mock TransactionIdempotencyClaimRepository claims;
    @Mock PlatformTransactionManager transactionManager;

    private TransactionIdempotencyClaimService service;

    @BeforeEach
    void setUp() {
        service = new TransactionIdempotencyClaimService(claims, transactionManager);
    }

    @Test
    void sameKeyAndSamePayloadReturnsOriginalDurableClaim() {
        TransactionIdempotencyClaim original = withdrawalClaim("25.00", "key-1");
        when(claims.findByUserIdAndIdempotencyKey("alice", "key-1"))
                .thenReturn(Optional.of(original));

        TransactionIdempotencyClaim replay = service.claimWithdrawal(
                "7", new BigDecimal("25.0"), "cash", "ref", "alice", "key-1");

        assertThat(replay).isSameAs(original);
    }

    @Test
    void sameKeyAndDifferentAmountConflictsBeforeRemoteCall() {
        TransactionIdempotencyClaim original = withdrawalClaim("25.00", "key-1");
        when(claims.findByUserIdAndIdempotencyKey("alice", "key-1"))
                .thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.claimWithdrawal(
                "7", new BigDecimal("30.00"), "cash", "ref", "alice", "key-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different transaction or reservation payload");
    }

    @Test
    void sameKeyAndDifferentCurrencyConflictsBeforeRemoteCall() {
        TransactionIdempotencyClaim original = withdrawalRequestClaim("25.00", "USD", "key-1");
        when(claims.findByUserIdAndIdempotencyKey("alice", "key-1"))
                .thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.claimWithdrawalRequest(
                "7", new BigDecimal("25.00"), "EUR", "cash", "ref", "alice", "key-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different transaction or reservation payload");
    }

    @Test
    void sameKeyAndSameCurrencyReturnsOriginalRequestClaim() {
        TransactionIdempotencyClaim original = withdrawalRequestClaim("25.00", "USD", "key-1");
        when(claims.findByUserIdAndIdempotencyKey("alice", "key-1"))
                .thenReturn(Optional.of(original));

        TransactionIdempotencyClaim replay = service.claimWithdrawalRequest(
                "7", new BigDecimal("25.0"), "usd", "cash", "ref", "alice", "key-1");

        assertThat(replay).isSameAs(original);
    }

    @Test
    void sameKeyAndDifferentOperationConflictsBeforeRemoteCall() {
        TransactionIdempotencyClaim original = withdrawalClaim("25.00", "key-1");
        when(claims.findByUserIdAndIdempotencyKey("alice", "key-1"))
                .thenReturn(Optional.of(original));
        TransferRequest transfer = TransferRequest.builder()
                .fromAccountId("7")
                .toAccountId("8")
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .build();

        assertThatThrownBy(() -> service.claimTransfer(transfer, "alice", "key-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different transaction or reservation payload");
    }

    @Test
    void concurrentSameKeyDifferentAmountHasOneWinnerAndOneConflict() throws Exception {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        AtomicReference<TransactionIdempotencyClaim> winner = new AtomicReference<>();
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch bothReadBeforeInsert = new CountDownLatch(2);

        when(claims.findByUserIdAndIdempotencyKey("alice", "key-race"))
                .thenAnswer(invocation -> {
                    if (reads.incrementAndGet() <= 2) {
                        bothReadBeforeInsert.countDown();
                        assertThat(bothReadBeforeInsert.await(5, TimeUnit.SECONDS)).isTrue();
                        return Optional.empty();
                    }
                    return Optional.ofNullable(winner.get());
                });
        when(claims.saveAndFlush(any(TransactionIdempotencyClaim.class)))
                .thenAnswer(invocation -> {
                    TransactionIdempotencyClaim candidate = invocation.getArgument(0);
                    if (winner.compareAndSet(null, candidate)) {
                        return candidate;
                    }
                    throw new DataIntegrityViolationException("unique idempotency claim");
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> capture(() -> service.claimWithdrawal(
                    "7", new BigDecimal("25.00"), "cash", "ref", "alice", "key-race")));
            Future<Object> second = executor.submit(() -> capture(() -> service.claimWithdrawal(
                    "7", new BigDecimal("30.00"), "cash", "ref", "alice", "key-race")));

            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(java.util.List.of(firstResult, secondResult)
                    .stream().filter(TransactionIdempotencyClaim.class::isInstance).count()).isEqualTo(1);
            assertThat(java.util.List.of(firstResult, secondResult)
                    .stream().filter(IllegalStateException.class::isInstance).count()).isEqualTo(1);
            assertThat(winner.get()).isNotNull();
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

    private TransactionIdempotencyClaim withdrawalClaim(String amount, String key) {
        LocalDateTime now = LocalDateTime.now();
        return TransactionIdempotencyClaim.builder()
                .claimId("claim-1")
                .userId("alice")
                .transactionType(TransactionType.WITHDRAWAL)
                .idempotencyKey(key)
                .requestFingerprint(hash("WITHDRAWAL|alice|7|"
                        + new BigDecimal(amount).stripTrailingZeros().toPlainString() + "|cash|ref"))
                .accountId("7")
                .operationType("WITHDRAWAL")
                .amount(new BigDecimal(amount))
                .state(TransactionIdempotencyClaimState.CLAIMED)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusMinutes(30))
                .build();
    }

    private TransactionIdempotencyClaim withdrawalRequestClaim(String amount, String currency, String key) {
        LocalDateTime now = LocalDateTime.now();
        return TransactionIdempotencyClaim.builder()
                .claimId("claim-1")
                .userId("alice")
                .transactionType(TransactionType.WITHDRAWAL)
                .idempotencyKey(key)
                .requestFingerprint(hash("WITHDRAWAL|alice|7|"
                        + new BigDecimal(amount).stripTrailingZeros().toPlainString()
                        + "|" + currency + "|cash|ref"))
                .accountId("7")
                .operationType("WITHDRAWAL")
                .amount(new BigDecimal(amount))
                .currency(currency)
                .state(TransactionIdempotencyClaimState.CLAIMED)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusMinutes(30))
                .build();
    }

    private String hash(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get();
    }
}
