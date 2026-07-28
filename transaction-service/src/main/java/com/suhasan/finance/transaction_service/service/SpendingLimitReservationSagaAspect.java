package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class SpendingLimitReservationSagaAspect {
    private final TransactionIdempotencyClaimService claimService;
    private final SpendingLimitReservationSagaCoordinator coordinator;
    private final SpendingLimitReservationSagaContext sagaContext;

    @Around("execution(* com.suhasan.finance.transaction_service.service.TransactionServiceImpl.processTransfer(..)) "
            + "&& args(request,userId,idempotencyKey)")
    public Object processTransfer(ProceedingJoinPoint joinPoint, TransferRequest request,
                                  String userId, String idempotencyKey) throws Throwable {
        claimService.claimTransfer(request, userId, idempotencyKey);
        try (SpendingLimitReservationSagaContext.Scope ignored = sagaContext.open(userId, idempotencyKey)) {
            try {
                TransactionResponse response = (TransactionResponse) joinPoint.proceed();
                reconcileCompleted(response, userId, idempotencyKey);
                return response;
            } catch (Throwable failure) {
                reconcileFailed(userId, TransactionType.TRANSFER, idempotencyKey, failure);
                throw failure;
            }
        }
    }

    @Around("execution(* com.suhasan.finance.transaction_service.service.TransactionServiceImpl.processWithdrawal(..)) "
            + "&& args(accountId,amount,description,reference,userId,idempotencyKey)")
    public Object processWithdrawal(ProceedingJoinPoint joinPoint, String accountId, BigDecimal amount,
                                    String description, String reference, String userId,
                                    String idempotencyKey) throws Throwable {
        TransactionIdempotencyClaim claim = claimService.find(userId, idempotencyKey)
                .orElseGet(() -> claimService.claimWithdrawal(
                        accountId, amount, description, reference, userId, idempotencyKey));
        requireCompatibleWithdrawalClaim(claim, accountId, amount);
        try (SpendingLimitReservationSagaContext.Scope ignored = sagaContext.open(userId, idempotencyKey)) {
            try {
                TransactionResponse response = (TransactionResponse) joinPoint.proceed();
                reconcileCompleted(response, userId, idempotencyKey);
                return response;
            } catch (Throwable failure) {
                reconcileFailed(userId, TransactionType.WITHDRAWAL, idempotencyKey, failure);
                throw failure;
            }
        }
    }

    private void requireCompatibleWithdrawalClaim(TransactionIdempotencyClaim claim,
                                                   String accountId, BigDecimal amount) {
        boolean amountMatches = claim.getAmount() != null && amount != null
                && claim.getAmount().compareTo(amount) == 0;
        if (claim.getTransactionType() != TransactionType.WITHDRAWAL
                || !Objects.equals(claim.getOperationType(), "WITHDRAWAL")
                || !Objects.equals(claim.getAccountId(), accountId)
                || !amountMatches) {
            throw new IllegalStateException(
                    "Idempotency-Key was reused with a different transaction or reservation payload");
        }
    }

    private void reconcileCompleted(TransactionResponse response, String userId, String idempotencyKey) {
        try {
            coordinator.completed(response, userId, idempotencyKey);
        } catch (RuntimeException reconciliationFailure) {
            log.error("Transaction completed but spending reservation reconciliation failed for key {}: {}",
                    idempotencyKey, reconciliationFailure.getMessage(), reconciliationFailure);
        }
    }

    private void reconcileFailed(String userId, TransactionType type, String idempotencyKey, Throwable failure) {
        try {
            coordinator.failed(userId, type, idempotencyKey, failure);
        } catch (RuntimeException reconciliationFailure) {
            log.error("Transaction failed and spending reservation reconciliation also failed for key {}: {}",
                    idempotencyKey, reconciliationFailure.getMessage(), reconciliationFailure);
        }
    }
}
