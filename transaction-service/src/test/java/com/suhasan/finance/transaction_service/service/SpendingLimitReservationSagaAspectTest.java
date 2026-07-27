package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState;
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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingLimitReservationSagaAspectTest {
    @Mock TransactionIdempotencyClaimService claimService;
    @Mock SpendingLimitReservationSagaCoordinator coordinator;
    @Mock ProceedingJoinPoint joinPoint;

    private SpendingLimitReservationSagaContext sagaContext;
    private SpendingLimitReservationSagaAspect aspect;

    @BeforeEach
    void setUp() {
        sagaContext = new SpendingLimitReservationSagaContext();
        aspect = new SpendingLimitReservationSagaAspect(claimService, coordinator, sagaContext);
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
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertThat(sagaContext.current()).hasValueSatisfying(context -> {
                assertThat(context.userId()).isEqualTo("alice");
                assertThat(context.idempotencyKey()).isEqualTo("key-1");
            });
            return response;
        });

        Object result = aspect.processTransfer(joinPoint, request, "alice", "key-1");

        assertThat(result).isSameAs(response);
        assertThat(sagaContext.current()).isEmpty();
        InOrder order = inOrder(claimService, joinPoint, coordinator);
        order.verify(claimService).claimTransfer(request, "alice", "key-1");
        order.verify(joinPoint).proceed();
        order.verify(coordinator).completed(response, "alice", "key-1");
    }

    @Test
    void localFailureAfterRemoteReservationKeepsOriginalFailureAndClearsContext() throws Throwable {
        RuntimeException localFailure = new RuntimeException("local transaction insert failed");
        TransactionIdempotencyClaim claim = withdrawalClaim();
        when(claimService.find("alice", "key-1")).thenReturn(Optional.empty());
        when(claimService.claimWithdrawal("7", new BigDecimal("25.00"),
                "cash", "ref", "alice", "key-1")).thenReturn(claim);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertThat(sagaContext.current()).isPresent();
            throw localFailure;
        });

        assertThatThrownBy(() -> aspect.processWithdrawal(joinPoint, "7", new BigDecimal("25.00"),
                "cash", "ref", "alice", "key-1"))
                .isSameAs(localFailure);

        assertThat(sagaContext.current()).isEmpty();
        InOrder order = inOrder(claimService, joinPoint, coordinator);
        order.verify(claimService).find("alice", "key-1");
        order.verify(claimService).claimWithdrawal("7", new BigDecimal("25.00"),
                "cash", "ref", "alice", "key-1");
        order.verify(joinPoint).proceed();
        order.verify(coordinator).failed("alice", TransactionType.WITHDRAWAL, "key-1", localFailure);
    }

    @Test
    void preclaimedWithdrawalRequestIsReusedWithoutWeakeningItsCurrencyFingerprint() throws Throwable {
        TransactionIdempotencyClaim claim = withdrawalClaim();
        TransactionResponse response = TransactionResponse.builder()
                .transactionId("tx-1")
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.COMPLETED)
                .build();
        when(claimService.find("alice", "key-1")).thenReturn(Optional.of(claim));
        when(joinPoint.proceed()).thenReturn(response);

        Object result = aspect.processWithdrawal(joinPoint, "7", new BigDecimal("25.00"),
                "cash", "ref", "alice", "key-1");

        assertThat(result).isSameAs(response);
        assertThat(sagaContext.current()).isEmpty();
        verify(claimService, never()).claimWithdrawal("7", new BigDecimal("25.00"),
                "cash", "ref", "alice", "key-1");
        verify(coordinator).completed(response, "alice", "key-1");
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
        assertThat(sagaContext.current()).isEmpty();
        verify(coordinator).completed(response, "alice", "key-1");
    }

    private TransactionIdempotencyClaim withdrawalClaim() {
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
                .currency("USD")
                .state(TransactionIdempotencyClaimState.CLAIMED)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusMinutes(30))
                .build();
    }
}
