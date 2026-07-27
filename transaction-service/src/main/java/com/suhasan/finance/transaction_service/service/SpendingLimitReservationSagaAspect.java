package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
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

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class SpendingLimitReservationSagaAspect {
    private final TransactionIdempotencyClaimService claimService;
    private final SpendingLimitReservationSagaCoordinator coordinator;

    @Around("execution(* com.suhasan.finance.transaction_service.service.TransactionServiceImpl.processTransfer(..)) "
            + "&& args(request,userId,idempotencyKey)")
    public Object processTransfer(ProceedingJoinPoint joinPoint, TransferRequest request,
                                  String userId, String idempotencyKey) throws Throwable {
        claimService.claimTransfer(request, userId, idempotencyKey);
        try {
            TransactionResponse response = (TransactionResponse) joinPoint.proceed();
            reconcileCompleted(response, userId, idempotencyKey);
            return response;
        } catch (Throwable failure) {
            reconcileFailed(userId, TransactionType.TRANSFER, idempotencyKey, failure);
            throw failure;
        }
    }

    @Around("execution(* com.suhasan.finance.transaction_service.service.TransactionServiceImpl.processWithdrawal(..)) "
            + "&& args(accountId,amount,description,reference,userId,idempotencyKey)")
    public Object processWithdrawal(ProceedingJoinPoint joinPoint, String accountId, BigDecimal amount,
                                    String description, String reference, String userId,
                                    String idempotencyKey) throws Throwable {
        claimService.claimWithdrawal(accountId, amount, description, reference, userId, idempotencyKey);
        try {
            TransactionResponse response = (TransactionResponse) joinPoint.proceed();
            reconcileCompleted(response, userId, idempotencyKey);
            return response;
        } catch (Throwable failure) {
            reconcileFailed(userId, TransactionType.WITHDRAWAL, idempotencyKey, failure);
            throw failure;
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
