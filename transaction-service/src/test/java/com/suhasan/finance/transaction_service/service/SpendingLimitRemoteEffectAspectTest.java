package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

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
    void captureIsMarkedAmbiguousBeforeRemoteCallAndRemainsHeldAfterAppliedResponse() throws Throwable {
        ResilientAccountServiceClient.DebitHoldResponse response = new ResilientAccountServiceClient.DebitHoldResponse(
                "hold-1", 7L, true, new BigDecimal("975.00"), new BigDecimal("975.00"),
                "CAPTURED", null);
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
    void rejectedCaptureReturnsClaimToSafeReservedState() throws Throwable {
        ResilientAccountServiceClient.DebitHoldResponse response = new ResilientAccountServiceClient.DebitHoldResponse(
                "hold-1", 7L, false, new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                "REJECTED", "Insufficient funds");
        when(joinPoint.proceed()).thenReturn(response);

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            aspect.captureDebitHold(joinPoint, "7", "hold-1", "tx-1", "WITHDRAWAL_CAPTURE");
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RESERVED, null, "DEBIT_CAPTURE_REJECTED");
    }

    @Test
    void captureTimeoutLeavesDurableClaimInReconciliationState() {
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
    void successfulTransferCompensationMakesDefinitiveReleaseSafe() throws Throwable {
        ResilientAccountServiceClient.BalanceOperationResponse response =
                new ResilientAccountServiceClient.BalanceOperationResponse(
                        7L, "tx-1:compensate", true, new BigDecimal("1000.00"),
                        3L, "APPLIED", null);
        when(joinPoint.proceed()).thenReturn(response);

        try (SpendingLimitReservationSagaContext.Scope ignored = context.open("alice", "key-1")) {
            aspect.balanceOperation(joinPoint, "7", "tx-1:compensate", new BigDecimal("25.00"),
                    "tx-1", "TRANSFER_COMPENSATION", true);
        }

        verify(claimService).updateState("alice", "key-1",
                TransactionIdempotencyClaimState.RESERVED,
                null, "TRANSFER_COMPENSATION_APPLIED");
    }

    @Test
    void ambiguousCompensationFailureKeepsReservationForReconciliation() {
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
}
