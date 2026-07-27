package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState;
import com.suhasan.finance.transaction_service.entity.TransactionProcessingState;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingLimitReservationSagaCoordinatorTest {
    @Mock TransactionIdempotencyClaimService claimService;
    @Mock SpendingLimitReservationLifecycleClient lifecycleClient;
    @Mock TransactionRepository transactionRepository;

    private SpendingLimitReservationSagaCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new SpendingLimitReservationSagaCoordinator(
                claimService, lifecycleClient, transactionRepository);
    }

    @Test
    void localFailureAfterRemoteReservationReleasesOrphan() {
        TransactionIdempotencyClaim claim = claim(TransactionIdempotencyClaimState.RESERVED,
                LocalDateTime.now().plusMinutes(20));
        when(claimService.require("alice", "key-1")).thenReturn(claim);
        when(transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(
                "alice", TransactionType.WITHDRAWAL, "key-1"))
                .thenReturn(Optional.empty());
        when(lifecycleClient.transition("7", 44L, "release", "alice", "claim-1",
                "LOCAL_TRANSACTION_NOT_CREATED"))
                .thenReturn(reservation("RELEASED"));

        coordinator.failed("alice", TransactionType.WITHDRAWAL, "key-1",
                new RuntimeException("local insert failed"));

        verify(lifecycleClient).transition("7", 44L, "release", "alice", "claim-1",
                "LOCAL_TRANSACTION_NOT_CREATED");
        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RELEASED, "RELEASED", null);
    }

    @Test
    void completedTransactionConsumesReservation() {
        TransactionIdempotencyClaim claim = claim(TransactionIdempotencyClaimState.RESERVED,
                LocalDateTime.now().plusMinutes(20));
        Transaction transaction = transaction(TransactionStatus.COMPLETED);
        when(claimService.require("alice", "key-1")).thenReturn(claim);
        when(transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(
                "alice", TransactionType.WITHDRAWAL, "key-1"))
                .thenReturn(Optional.of(transaction));
        when(lifecycleClient.transition("7", 44L, "consume", "alice", "claim-1",
                "TRANSACTION_COMPLETED"))
                .thenReturn(reservation("CONSUMED"));

        coordinator.failed("alice", TransactionType.WITHDRAWAL, "key-1",
                new RuntimeException("response path failed after commit"));

        verify(lifecycleClient).transition("7", 44L, "consume", "alice", "claim-1",
                "TRANSACTION_COMPLETED");
        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.COMPLETED, "CONSUMED", null);
        verify(lifecycleClient, never()).transition("7", 44L, "release", "alice", "claim-1",
                "TRANSACTION_DEFINITIVELY_FAILED");
    }

    @Test
    void manualActionOutcomeIsNeverReleased() {
        TransactionIdempotencyClaim claim = claim(TransactionIdempotencyClaimState.RESERVED,
                LocalDateTime.now().minusMinutes(1));
        Transaction transaction = transaction(TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION);
        when(claimService.require("alice", "key-1")).thenReturn(claim);
        when(transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(
                "alice", TransactionType.WITHDRAWAL, "key-1"))
                .thenReturn(Optional.of(transaction));
        when(lifecycleClient.transition("7", 44L, "reconciliation-required", "alice", "claim-1",
                "AMBIGUOUS_TRANSACTION_OUTCOME"))
                .thenReturn(reservation("RECONCILIATION_REQUIRED"));

        coordinator.failed("alice", TransactionType.WITHDRAWAL, "key-1",
                new RuntimeException("ambiguous timeout"));

        verify(lifecycleClient).transition("7", 44L, "reconciliation-required", "alice", "claim-1",
                "AMBIGUOUS_TRANSACTION_OUTCOME");
        verify(lifecycleClient, never()).transition(eq("7"), eq(44L), eq("release"),
                eq("alice"), eq("claim-1"), any());
        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                "RESERVED", "AMBIGUOUS_TRANSACTION_OUTCOME");
    }

    @Test
    void expiredClaimWithoutTransactionIsReleasedByScheduledReconciliation() {
        TransactionIdempotencyClaim claim = claim(TransactionIdempotencyClaimState.RESERVED,
                LocalDateTime.now().minusMinutes(1));
        when(claimService.staleClaims(any(), any())).thenReturn(List.of(claim));
        when(claimService.require("alice", "key-1")).thenReturn(claim);
        when(transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(
                "alice", TransactionType.WITHDRAWAL, "key-1"))
                .thenReturn(Optional.empty());
        when(lifecycleClient.transition("7", 44L, "release", "alice", "claim-1",
                "LOCAL_TRANSACTION_NOT_CREATED"))
                .thenReturn(reservation("RELEASED"));

        coordinator.reconcileStaleClaims();

        verify(lifecycleClient).transition("7", 44L, "release", "alice", "claim-1",
                "LOCAL_TRANSACTION_NOT_CREATED");
    }

    private TransactionIdempotencyClaim claim(TransactionIdempotencyClaimState state,
                                               LocalDateTime expiresAt) {
        LocalDateTime now = LocalDateTime.now();
        return TransactionIdempotencyClaim.builder()
                .claimId("claim-1")
                .userId("alice")
                .transactionType(TransactionType.WITHDRAWAL)
                .idempotencyKey("key-1")
                .requestFingerprint("fingerprint")
                .accountId("7")
                .operationType("WITHDRAWAL")
                .amount(new BigDecimal("25.00"))
                .state(state)
                .reservationId(44L)
                .reservationFingerprint("reservation-fingerprint")
                .reservationAmount(new BigDecimal("25.00"))
                .reservationCurrency("USD")
                .reservationState("RESERVED")
                .createdAt(now.minusMinutes(2))
                .updatedAt(now.minusMinutes(2))
                .expiresAt(expiresAt)
                .build();
    }

    private Transaction transaction(TransactionStatus status) {
        return Transaction.builder()
                .transactionId("tx-1")
                .fromAccountId("7")
                .toAccountId("EXTERNAL")
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .type(TransactionType.WITHDRAWAL)
                .status(status)
                .processingState(status == TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION
                        ? TransactionProcessingState.MANUAL_ACTION_REQUIRED
                        : TransactionProcessingState.COMPLETED)
                .idempotencyKey("key-1")
                .createdBy("alice")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private SpendingLimitReservationLifecycleClient.ReservationResponse reservation(String state) {
        return new SpendingLimitReservationLifecycleClient.ReservationResponse(
                true, false, "USD", new BigDecimal("100.00"),
                new BigDecimal("25.00"), new BigDecimal("75.00"), null,
                44L, "claim-1", new BigDecimal("25.00"),
                "reservation-fingerprint", state, LocalDateTime.now(),
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(30), state);
    }
}
