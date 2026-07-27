package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState;
import com.suhasan.finance.transaction_service.entity.TransactionProcessingState;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingLimitReservationClientAspectTest {
    @Mock TransactionIdempotencyClaimService claimService;
    @Mock SpendingLimitReservationLifecycleClient lifecycleClient;
    @Mock TransactionRepository transactionRepository;
    @Mock ProceedingJoinPoint joinPoint;

    private SpendingLimitReservationClientAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new SpendingLimitReservationClientAspect(
                claimService, lifecycleClient, transactionRepository);
    }

    @Test
    void legacyReleaseIsSuppressedForManualActionOutcome() throws Throwable {
        TransactionIdempotencyClaim claim = claim();
        Transaction transaction = Transaction.builder()
                .transactionId("tx-1")
                .fromAccountId("7")
                .toAccountId("EXTERNAL")
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION)
                .processingState(TransactionProcessingState.MANUAL_ACTION_REQUIRED)
                .createdBy("alice")
                .idempotencyKey("key-1")
                .createdAt(LocalDateTime.now())
                .build();
        when(claimService.find("alice", "key-1")).thenReturn(Optional.of(claim));
        when(transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(
                "alice", TransactionType.WITHDRAWAL, "key-1"))
                .thenReturn(Optional.of(transaction));
        when(lifecycleClient.transition("7", 44L, "reconciliation-required", "alice",
                "claim-1", "AMBIGUOUS_TRANSACTION_OUTCOME"))
                .thenReturn(reservation("RECONCILIATION_REQUIRED"));

        aspect.release(joinPoint, "7", "WITHDRAWAL", "key-1", "alice");

        verify(lifecycleClient).transition("7", 44L, "reconciliation-required", "alice",
                "claim-1", "AMBIGUOUS_TRANSACTION_OUTCOME");
        verify(lifecycleClient, never()).transition(eq("7"), eq(44L), eq("release"),
                eq("alice"), eq("claim-1"), any());
        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                "RECONCILIATION_REQUIRED",
                "Legacy release suppressed because the transaction outcome is ambiguous");
        verify(joinPoint, never()).proceed();
    }

    private TransactionIdempotencyClaim claim() {
        LocalDateTime now = LocalDateTime.now();
        return TransactionIdempotencyClaim.builder()
                .claimId("claim-1")
                .userId("alice")
                .transactionType(TransactionType.WITHDRAWAL)
                .idempotencyKey("key-1")
                .requestFingerprint("claim-fingerprint")
                .accountId("7")
                .operationType("WITHDRAWAL")
                .amount(new BigDecimal("25.00"))
                .state(TransactionIdempotencyClaimState.RESERVED)
                .reservationId(44L)
                .reservationFingerprint("reservation-fingerprint")
                .reservationAmount(new BigDecimal("25.00"))
                .reservationCurrency("USD")
                .reservationState("RESERVED")
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusMinutes(30))
                .build();
    }

    private SpendingLimitReservationLifecycleClient.ReservationResponse reservation(String state) {
        return new SpendingLimitReservationLifecycleClient.ReservationResponse(
                true, true, "USD", new BigDecimal("100.00"),
                new BigDecimal("25.00"), new BigDecimal("75.00"), null,
                44L, "claim-1", new BigDecimal("25.00"),
                "reservation-fingerprint", state, LocalDateTime.now(),
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(30), state);
    }
}
