package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.StepUpClientDtos;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.TransferAuthorization;
import com.suhasan.finance.transaction_service.entity.TransferAuthorizationStatus;
import com.suhasan.finance.transaction_service.exception.InsufficientFundsException;
import com.suhasan.finance.transaction_service.repository.TransferAuthorizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferAuthorizationServiceTest {
    @Mock TransferAuthorizationPolicy policy;
    @Mock TransferAuthorizationRepository authorizationRepository;
    @Mock ResilientAccountServiceClient accountServiceClient;
    @Mock TransactionService transactionService;
    @Mock AuditService auditService;

    private TransferAuthorizationService service;
    private TransferAuthorizationStateService authorizationStateService;

    @BeforeEach
    void setUp() {
        authorizationStateService = new TransferAuthorizationStateService(authorizationRepository, accountServiceClient);
        service = new TransferAuthorizationService(policy, authorizationRepository, accountServiceClient,
                transactionService, auditService, authorizationStateService);
        org.mockito.Mockito.lenient().when(authorizationRepository.save(any(TransferAuthorization.class))).thenAnswer(invocation -> {
            TransferAuthorization authorization = invocation.getArgument(0);
            if (authorization.getAuthorizationId() == null) authorization.setAuthorizationId("authorization-1");
            if (authorization.getCreatedAt() == null) authorization.setCreatedAt(Instant.parse("2026-07-13T12:00:00Z"));
            return authorization;
        });
    }

    @Test
    void lowRiskTransferExecutesImmediatelyWithNormalizedIdempotencyKey() {
        TransferRequest request = request();
        TransactionResponse executed = TransactionResponse.builder().transactionId("transaction-1").build();
        when(authorizationRepository.findByUserIdAndIdempotencyKey("alice", "request-1"))
                .thenReturn(Optional.empty());
        when(policy.evaluate(request, "alice")).thenReturn(List.of());
        when(transactionService.processTransfer(request, "alice", "request-1")).thenReturn(executed);

        assertThat(service.submit(request, "alice", "  request-1  ")).isSameAs(executed);

        verify(accountServiceClient, never()).createStepUpChallenge(any());
        verify(authorizationRepository, never()).save(any());
    }

    @Test
    void riskyTransferCreatesPendingAuthorizationEvenWhenNotificationFails() {
        TransferRequest request = request();
        when(authorizationRepository.findByUserIdAndIdempotencyKey("alice", "request-1"))
                .thenReturn(Optional.empty());
        when(policy.evaluate(request, "alice")).thenReturn(List.of(
                TransferAuthorizationReason.HIGH_VALUE_TRANSFER,
                TransferAuthorizationReason.NEW_BENEFICIARY));
        when(accountServiceClient.createStepUpChallenge(any())).thenReturn(
                new StepUpClientDtos.CreateChallengeResponse(
                        "challenge-1", Instant.parse("2026-07-13T12:05:00Z")));
        doThrow(new RuntimeException("notification unavailable"))
                .when(accountServiceClient).createNotification(any());

        TransactionResponse response = service.submit(request, "alice", "request-1");

        assertThat(response.getTransactionId()).isEqualTo("authorization-1");
        assertThat(response.getAuthorizationRequired()).isTrue();
        assertThat(response.getAuthorizationChallengeId()).isEqualTo("challenge-1");
        assertThat(response.getAuthorizationReasons()).containsExactly(
                "HIGH_VALUE_TRANSFER", "NEW_BENEFICIARY");
        assertThat(response.getProcessingState()).isEqualTo("AWAITING_AUTHORIZATION");
        verify(transactionService, never()).processTransfer(any(), any(), any());
        verify(auditService).logSecurityEvent(eq("STEP_UP_REQUIRED"), eq("alice"), any(), eq(null));
    }

    @Test
    void repeatedIdempotencyKeyRejectsAChangedTransferFingerprint() {
        TransferRequest request = request();
        TransferAuthorization existing = authorization(TransferAuthorizationStatus.PENDING);
        existing.setActionFingerprint("different-fingerprint");
        when(authorizationRepository.findByUserIdAndIdempotencyKey("alice", "request-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.submit(request, "alice", "request-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different transfer");

        verify(policy, never()).evaluate(any(), any());
        verify(transactionService, never()).processTransfer(any(), any(), any());
    }

    @Test
    void authorizeConsumesProofAndMarksTransferCompleted() {
        TransferAuthorization authorization = authorization(TransferAuthorizationStatus.PENDING);
        when(authorizationRepository.findByIdWithLock("authorization-1"))
                .thenReturn(Optional.of(authorization));
        TransactionResponse executed = TransactionResponse.builder().transactionId("transaction-1").build();
        when(transactionService.processTransfer(any(TransferRequest.class), eq("alice"), eq("request-1")))
                .thenReturn(executed);

        assertThat(service.authorize("authorization-1", "alice", "proof-1")).isSameAs(executed);

        verify(accountServiceClient).consumeStepUpChallenge(eq("challenge-1"),
                eq(new StepUpClientDtos.ConsumeChallengeRequest(
                        "alice", "fingerprint", "authorization-1", "proof-1")));
        assertThat(authorization.getStatus()).isEqualTo(TransferAuthorizationStatus.COMPLETED);
        assertThat(authorization.getExecutedTransactionId()).isEqualTo("transaction-1");
        assertThat(authorization.getAuthorizedAt()).isNotNull();
        assertThat(authorization.getCompletedAt()).isNotNull();
    }

    @Test
    void failedExecutionRemainsAuthorizedSoIdempotentRetryCanResume() {
        TransferAuthorization authorization = authorization(TransferAuthorizationStatus.PENDING);
        when(authorizationRepository.findByIdWithLock("authorization-1"))
                .thenReturn(Optional.of(authorization));
        when(transactionService.processTransfer(any(TransferRequest.class), eq("alice"), eq("request-1")))
                .thenThrow(new IllegalStateException("downstream unavailable"));

        assertThatThrownBy(() -> service.authorize("authorization-1", "alice", "proof-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("downstream unavailable");

        assertThat(authorization.getStatus()).isEqualTo(TransferAuthorizationStatus.AUTHORIZED);
        assertThat(authorization.getExecutedTransactionId()).isNull();
        verify(authorizationRepository, org.mockito.Mockito.atLeast(1)).save(authorization);
    }

    @Test
    void insufficientFundsFailsAuthorizationAndDoesNotLeaveItHanging() {
        TransferAuthorization authorization = authorization(TransferAuthorizationStatus.PENDING);
        when(authorizationRepository.findByIdWithLock("authorization-1"))
                .thenReturn(Optional.of(authorization));
        when(transactionService.processTransfer(any(TransferRequest.class), eq("alice"), eq("request-1")))
                .thenThrow(new InsufficientFundsException("Insufficient funds. No money moved."));

        assertThatThrownBy(() -> service.authorize("authorization-1", "alice", "proof-1"))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient funds. No money moved.");

        assertThat(authorization.getStatus()).isEqualTo(TransferAuthorizationStatus.FAILED);
        assertThat(authorization.getExecutedTransactionId()).isNull();
        verify(auditService).logSecurityEvent(eq("STEP_UP_EXECUTION_FAILED"), eq("alice"), any(), eq(null));
        verify(accountServiceClient).createNotification(any());
    }

    @Test
    void expiredAuthorizationIsCancelledBeforeProofConsumption() {
        TransferAuthorization authorization = authorization(TransferAuthorizationStatus.PENDING);
        authorization.setExpiresAt(Instant.now().minusSeconds(1));
        when(authorizationRepository.findByIdWithLock("authorization-1"))
                .thenReturn(Optional.of(authorization));

        assertThatThrownBy(() -> service.authorize("authorization-1", "alice", "proof-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Transfer authorization has expired");

        assertThat(authorization.getStatus()).isEqualTo(TransferAuthorizationStatus.CANCELLED);
        verify(accountServiceClient, never()).consumeStepUpChallenge(any(), any());
        verify(transactionService, never()).processTransfer(any(), any(), any());
        verify(auditService).logSecurityEvent(eq("STEP_UP_EXPIRED"), eq("alice"), any(), eq(null));
    }

    private TransferRequest request() {
        return TransferRequest.builder()
                .fromAccountId("account-1")
                .toAccountId("account-2")
                .beneficiaryId("beneficiary-1")
                .amount(new BigDecimal("5000.00"))
                .currency("USD")
                .description("Invoice")
                .reference("INV-1")
                .build();
    }

    private TransferAuthorization authorization(TransferAuthorizationStatus status) {
        TransferAuthorization authorization = new TransferAuthorization();
        authorization.setAuthorizationId("authorization-1");
        authorization.setUserId("alice");
        authorization.setIdempotencyKey("request-1");
        authorization.setActionFingerprint("fingerprint");
        authorization.setChallengeId("challenge-1");
        authorization.setFromAccountId("account-1");
        authorization.setToAccountId("account-2");
        authorization.setBeneficiaryId("beneficiary-1");
        authorization.setAmount(new BigDecimal("5000.00"));
        authorization.setCurrency("USD");
        authorization.setDescription("Invoice");
        authorization.setReference("INV-1");
        authorization.setReasonCodes("HIGH_VALUE_TRANSFER");
        authorization.setStatus(status);
        authorization.setExpiresAt(Instant.now().plusSeconds(300));
        authorization.setCreatedAt(Instant.parse("2026-07-13T12:00:00Z"));
        return authorization;
    }
}
