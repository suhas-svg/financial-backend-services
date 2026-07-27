package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingLimitReservationSagaAspectTest {
    @Mock TransactionIdempotencyClaimService claimService;
    @Mock SpendingLimitReservationSagaCoordinator coordinator;
    @Mock ProceedingJoinPoint joinPoint;

    private SpendingLimitReservationSagaAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new SpendingLimitReservationSagaAspect(claimService, coordinator);
    }

    @Test
    void claimsDurablyBeforeTransferProcessingAndReconcilesCompletion() throws Throwable {
        TransferRequest request = TransferRequest.builder()
                .fromAccountId("7")
                .toAccountId("8")
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .build();
        TransactionResponse response = TransactionResponse.builder()
                .transactionId("tx-1")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .build();
        when(joinPoint.proceed()).thenReturn(response);

        Object result = aspect.processTransfer(joinPoint, request, "alice", "key-1");

        assertThat(result).isSameAs(response);
        InOrder order = inOrder(claimService, joinPoint, coordinator);
        order.verify(claimService).claimTransfer(request, "alice", "key-1");
        order.verify(joinPoint).proceed();
        order.verify(coordinator).completed(response, "alice", "key-1");
    }

    @Test
    void localFailureAfterRemoteReservationKeepsOriginalFailureAndRunsReconciliation() throws Throwable {
        RuntimeException localFailure = new RuntimeException("local transaction insert failed");
        when(joinPoint.proceed()).thenThrow(localFailure);

        assertThatThrownBy(() -> aspect.processWithdrawal(joinPoint, "7", new BigDecimal("25.00"),
                "cash", "ref", "alice", "key-1"))
                .isSameAs(localFailure);

        InOrder order = inOrder(claimService, joinPoint, coordinator);
        order.verify(claimService).claimWithdrawal("7", new BigDecimal("25.00"),
                "cash", "ref", "alice", "key-1");
        order.verify(joinPoint).proceed();
        order.verify(coordinator).failed("alice", TransactionType.WITHDRAWAL, "key-1", localFailure);
    }

    @Test
    void reconciliationFailureDoesNotMaskCompletedTransaction() throws Throwable {
        TransferRequest request = TransferRequest.builder()
                .fromAccountId("7")
                .toAccountId("8")
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .build();
        TransactionResponse response = TransactionResponse.builder()
                .transactionId("tx-1")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .build();
        when(joinPoint.proceed()).thenReturn(response);
        org.mockito.Mockito.doThrow(new RuntimeException("account service unavailable"))
                .when(coordinator).completed(response, "alice", "key-1");

        Object result = aspect.processTransfer(joinPoint, request, "alice", "key-1");

        assertThat(result).isSameAs(response);
        verify(coordinator).completed(response, "alice", "key-1");
    }
}
