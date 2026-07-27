package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 150)
public class SpendingLimitRemoteEffectAspect {
    private static final String TRANSFER_COMPENSATION = "TRANSFER_COMPENSATION";

    private final SpendingLimitReservationSagaContext sagaContext;
    private final TransactionIdempotencyClaimService claimService;

    @Around("execution(* com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient.captureDebitHold(..)) "
            + "&& args(accountId,holdId,transactionId,reason)")
    public Object captureDebitHold(ProceedingJoinPoint joinPoint, String accountId, String holdId,
                                   String transactionId, String reason) throws Throwable {
        Optional<SpendingLimitReservationSagaContext.Context> active = sagaContext.current();
        if (active.isEmpty()) {
            return joinPoint.proceed();
        }
        SpendingLimitReservationSagaContext.Context context = active.get();
        claimService.updateState(context.userId(), context.idempotencyKey(),
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                null, "DEBIT_CAPTURE_IN_FLIGHT");
        try {
            Object result = joinPoint.proceed();
            if (result instanceof ResilientAccountServiceClient.DebitHoldResponse response) {
                if (response.isApplied()) {
                    safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                            "DEBIT_CAPTURE_APPLIED_AWAITING_LOCAL_COMMIT");
                } else {
                    safeUpdate(context, TransactionIdempotencyClaimState.RESERVED,
                            "DEBIT_CAPTURE_REJECTED");
                }
            }
            return result;
        } catch (Throwable failure) {
            safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                    "DEBIT_CAPTURE_OUTCOME_AMBIGUOUS: " + bounded(failure.getMessage()));
            throw failure;
        }
    }

    @Around("execution(* com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient.applyBalanceOperation(..)) "
            + "&& args(accountId,operationId,delta,transactionId,reason,allowNegative)")
    public Object balanceOperation(ProceedingJoinPoint joinPoint, String accountId, String operationId,
                                   BigDecimal delta, String transactionId, String reason,
                                   boolean allowNegative) throws Throwable {
        Optional<SpendingLimitReservationSagaContext.Context> active = sagaContext.current();
        if (active.isEmpty() || !TRANSFER_COMPENSATION.equals(reason)) {
            return joinPoint.proceed();
        }
        SpendingLimitReservationSagaContext.Context context = active.get();
        try {
            Object result = joinPoint.proceed();
            if (result instanceof ResilientAccountServiceClient.BalanceOperationResponse response
                    && response.isApplied()) {
                safeUpdate(context, TransactionIdempotencyClaimState.RESERVED,
                        "TRANSFER_COMPENSATION_APPLIED");
            } else {
                safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                        "TRANSFER_COMPENSATION_NOT_CONFIRMED");
            }
            return result;
        } catch (Throwable failure) {
            safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                    "TRANSFER_COMPENSATION_OUTCOME_AMBIGUOUS: " + bounded(failure.getMessage()));
            throw failure;
        }
    }

    private void safeUpdate(SpendingLimitReservationSagaContext.Context context,
                            TransactionIdempotencyClaimState state, String details) {
        try {
            claimService.updateState(context.userId(), context.idempotencyKey(), state, null, details);
        } catch (RuntimeException persistenceFailure) {
            log.error("Failed to persist spending reservation remote-effect state for key {}: {}",
                    context.idempotencyKey(), persistenceFailure.getMessage(), persistenceFailure);
        }
    }

    private String bounded(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.length() <= 800 ? message : message.substring(0, 800);
    }
}
