package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingLimitRemoteEffectAspectTest {
    @Mock TransactionIdempotencyClaimService claimService;
    @Mock ProceedingJoinPoint joinPoint;

    private SpendingLimitReservationSagaContext context;
    private SpendingLimitRemoteEffectAspect aspect;

    @BeforeEach
    void setUp() {
        context = new SpendingLimitReservationSagaContext();
        aspect = new SpendingLimitRemoteEffectAspect(context, claimService);
    }

    @Test
    void holdPlacementIsMarkedBeforeRemoteCallAndRemainsHeldUntilCaptureOrRelease() throws Throwable {
        ResilientAccountServiceClient.DebitHoldResponse response = holdResponse(true, "PLACED");
        when(joinPoint.proceed()).thenReturn(response);

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            assertThat(aspect.placeDebitHold(joinPoint, "7", "hold-1", new BigDecimal("25.00"),
                    "tx-1", "WITHDRAWAL_HOLD")).isSameAs(response);
        }

        InOrder order = inOrder(claimService, joinPoint);
        order.verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "DEBIT_HOLD_PLACEMENT_IN_FLIGHT");
        order.verify(joinPoint).proceed();
        order.verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "DEBIT_HOLD_PLACED_AWAITING_CAPTURE_OR_RELEASE");
    }

    @Test
    void rejectedHoldPlacementReturnsClaimToSafeReservedState() throws Throwable {
        when(joinPoint.proceed()).thenReturn(holdResponse(false, "REJECTED"));

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            aspect.placeDebitHold(joinPoint, "7", "hold-1", new BigDecimal("25.00"),
                    "tx-1", "WITHDRAWAL_HOLD");
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RESERVED,
                null, "DEBIT_HOLD_PLACEMENT_REJECTED");
    }

    @Test
    void placementTimeoutLeavesDurableClaimInReconciliationState() throws Throwable {
        RuntimeException timeout = new RuntimeException("placement timeout");
        when(joinPoint.proceed()).thenThrow(timeout);

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            assertThatThrownBy(() -> aspect.placeDebitHold(joinPoint, "7", "hold-1",
                    new BigDecimal("25.00"), "tx-1", "WITHDRAWAL_HOLD"))
                    .isSameAs(timeout);
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "DEBIT_HOLD_PLACEMENT_OUTCOME_AMBIGUOUS: placement timeout");
    }

    @Test
    void captureIsMarkedAmbiguousBeforeRemoteCallAndRemainsHeldAfterAppliedResponse() throws Throwable {
        ResilientAccountServiceClient.DebitHoldResponse response = holdResponse(true, "CAPTURED");
        when(joinPoint.proceed()).thenReturn(response);

        Object result;
        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            result = aspect.captureDebitHold(joinPoint, "7", "hold-1", "tx-1", "WITHDRAWAL_CAPTURE");
        }

        assertThat(result).isSameAs(response);
        InOrder order = inOrder(claimService, joinPoint);
        order.verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "DEBIT_CAPTURE_IN_FLIGHT");
        order.verify(joinPoint).proceed();
        order.verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "DEBIT_CAPTURE_APPLIED_AWAITING_LOCAL_COMMIT");
    }

    @Test
    void rejectedCaptureRemainsAmbiguousUntilThePlacedHoldIsReleased() throws Throwable {
        when(joinPoint.proceed()).thenReturn(holdResponse(false, "PLACED"));

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            aspect.captureDebitHold(joinPoint, "7", "hold-1", "tx-1", "WITHDRAWAL_CAPTURE");
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "DEBIT_CAPTURE_REJECTED_HOLD_REQUIRES_RELEASE");
    }

    @Test
    void successfulHoldReleaseMakesReservationReleaseSafe() throws Throwable {
        when(joinPoint.proceed()).thenReturn(holdResponse(true, "RELEASED"));

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            aspect.releaseDebitHold(joinPoint, "7", "hold-1", "tx-1", "CAPTURE_FAILED");
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RESERVED,
                null, "DEBIT_HOLD_RELEASED");
    }

    @Test
    void unconfirmedHoldReleaseKeepsReservationForReconciliation() throws Throwable {
        when(joinPoint.proceed()).thenReturn(holdResponse(false, "CAPTURED"));

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            aspect.releaseDebitHold(joinPoint, "7", "hold-1", "tx-1", "CAPTURE_FAILED");
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "DEBIT_HOLD_RELEASE_NOT_CONFIRMED");
    }

    @Test
    void captureTimeoutLeavesDurableClaimInReconciliationState() throws Throwable {
        RuntimeException timeout = new RuntimeException("capture timeout");
        when(joinPoint.proceed()).thenThrow(timeout);

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            assertThatThrownBy(() -> aspect.captureDebitHold(
                    joinPoint, "7", "hold-1", "tx-1", "WITHDRAWAL_CAPTURE"))
                    .isSameAs(timeout);
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "DEBIT_CAPTURE_OUTCOME_AMBIGUOUS: capture timeout");
    }

    @Test
    void appliedDestinationCreditRemainsAmbiguousUntilLocalCommit() throws Throwable {
        ResilientAccountServiceClient.BalanceOperationResponse response = balanceResponse(true, null);
        when(joinPoint.proceed()).thenReturn(response);

        Object result;
        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            result = aspect.balanceOperation(joinPoint, "8", "tx-1:credit", new BigDecimal("25.00"),
                    "tx-1", "TRANSFER_CREDIT", true);
        }

        assertThat(result).isSameAs(response);
        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "TRANSFER_CREDIT_IN_FLIGHT");
        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "TRANSFER_CREDIT_APPLIED_AWAITING_LOCAL_COMMIT");
    }

    @Test
    void rejectedDestinationCreditForcesCompensation() throws Throwable {
        when(joinPoint.proceed()).thenReturn(balanceResponse(false, "credit rejected"));

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            assertThatThrownBy(() -> aspect.balanceOperation(joinPoint, "8", "tx-1:credit",
                    new BigDecimal("25.00"), "tx-1", "TRANSFER_CREDIT", true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("credit rejected");
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "TRANSFER_CREDIT_REJECTED_SOURCE_DEBIT_REQUIRES_COMPENSATION");
    }

    @Test
    void destinationCreditTimeoutRemainsAmbiguous() throws Throwable {
        RuntimeException timeout = new RuntimeException("credit timeout");
        when(joinPoint.proceed()).thenThrow(timeout);

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            assertThatThrownBy(() -> aspect.balanceOperation(joinPoint, "8", "tx-1:credit",
                    new BigDecimal("25.00"), "tx-1", "TRANSFER_CREDIT", true))
                    .isSameAs(timeout);
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "TRANSFER_CREDIT_OUTCOME_AMBIGUOUS: credit timeout");
    }

    @Test
    void successfulCompensationBeforeDestinationCreditMakesDefinitiveReleaseSafe() throws Throwable {
        when(claimService.require("alice", "key-1"))
                .thenReturn(claim("DEBIT_CAPTURE_APPLIED_AWAITING_LOCAL_COMMIT"));
        when(joinPoint.proceed()).thenReturn(balanceResponse(true, null));

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            aspect.balanceOperation(joinPoint, "7", "tx-1:compensate", new BigDecimal("25.00"),
                    "tx-1", "TRANSFER_COMPENSATION", true);
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RESERVED,
                null, "TRANSFER_COMPENSATION_APPLIED");
    }

    @Test
    void compensationCannotClearAmbiguousDestinationCredit() throws Throwable {
        when(claimService.require("alice", "key-1"))
                .thenReturn(claim("TRANSFER_CREDIT_OUTCOME_AMBIGUOUS: credit timeout"));
        when(joinPoint.proceed()).thenReturn(balanceResponse(true, null));

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            aspect.balanceOperation(joinPoint, "7", "tx-1:compensate", new BigDecimal("25.00"),
                    "tx-1", "TRANSFER_COMPENSATION", true);
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "SOURCE_COMPENSATED_BUT_TRANSFER_CREDIT_REQUIRES_RECONCILIATION");
        verify(claimService, never()).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RESERVED,
                null, "TRANSFER_COMPENSATION_APPLIED");
    }

    @Test
    void ambiguousCompensationFailureKeepsReservationForReconciliation() throws Throwable {
        when(claimService.require("alice", "key-1"))
                .thenReturn(claim("DEBIT_CAPTURE_APPLIED_AWAITING_LOCAL_COMMIT"));
        RuntimeException timeout = new RuntimeException("compensation timeout");
        when(joinPoint.proceed()).thenThrow(timeout);

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            assertThatThrownBy(() -> aspect.balanceOperation(joinPoint, "7", "tx-1:compensate",
                    new BigDecimal("25.00"), "tx-1", "TRANSFER_COMPENSATION", true))
                    .isSameAs(timeout);
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "TRANSFER_COMPENSATION_OUTCOME_AMBIGUOUS: compensation timeout");
    }

    @Test
    void unrelatedCallsAndCallsOutsideSagaAreNotMarked() throws Throwable {
        Object response = new Object();
        when(joinPoint.proceed()).thenReturn(response);

        assertThat(aspect.captureDebitHold(joinPoint, "7", "hold-1", "tx-1", "CAPTURE"))
                .isSameAs(response);
        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            assertThat(aspect.balanceOperation(joinPoint, "7", "op-1", BigDecimal.ONE,
                    "tx-1", "UNRELATED", true)).isSameAs(response);
        }

        verify(claimService, never()).updateState(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private TransactionIdempotencyClaim claim(String details) {
        LocalDateTime now = LocalDateTime.now();
        return TransactionIdempotencyClaim.builder()
                .claimId("claim-1")
                .userId("alice")
                .transactionType(TransactionType.TRANSFER)
                .idempotencyKey("key-1")
                .requestFingerprint("fingerprint")
                .accountId("7")
                .operationType("TRANSFER")
                .amount(new BigDecimal("25.00"))
                .state(TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED)
                .failureDetails(details)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusMinutes(30))
                .build();
    }

    private ResilientAccountServiceClient.DebitHoldResponse holdResponse(boolean applied, String status) {
        return new ResilientAccountServiceClient.DebitHoldResponse(
                "hold-1", 7L, applied, new BigDecimal("975.00"), new BigDecimal("975.00"),
                status, null);
    }

    private ResilientAccountServiceClient.BalanceOperationResponse balanceResponse(
            boolean applied, String message) {
        return new ResilientAccountServiceClient.BalanceOperationResponse(
                7L, "operation-1", applied, new BigDecimal("1000.00"),
                3L, applied ? "APPLIED" : "REJECTED", message);
    }
}
