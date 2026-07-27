package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
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
    private static final String TRANSFER_CREDIT = "TRANSFER_CREDIT";
    private static final String TRANSFER_COMPENSATION = "TRANSFER_COMPENSATION";

    private final SpendingLimitReservationSagaContext sagaContext;
    private final TransactionIdempotencyClaimService claimService;

    @Around("execution(* com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient.placeDebitHold(..)) "
            + "&& args(accountId,holdId,amount,transactionId,reason)")
    public Object placeDebitHold(ProceedingJoinPoint joinPoint, String accountId, String holdId,
                                 BigDecimal amount, String transactionId, String reason) throws Throwable {
        Optional<SpendingLimitReservationSagaContext.Context> active = sagaContext.current();
        if (active.isEmpty()) {
            return joinPoint.proceed();
        }
        SpendingLimitReservationSagaContext.Context context = active.get();
        markBeforeRemoteEffect(context, "DEBIT_HOLD_PLACEMENT_IN_FLIGHT");
        try {
            Object result = joinPoint.proceed();
            if (result instanceof ResilientAccountServiceClient.DebitHoldResponse response) {
                if (response.isApplied()) {
                    safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                            "DEBIT_HOLD_PLACED_AWAITING_CAPTURE_OR_RELEASE");
                } else {
                    safeUpdate(context, TransactionIdempotencyClaimState.RESERVED,
                            "DEBIT_HOLD_PLACEMENT_REJECTED");
                }
            }
            return result;
        } catch (Throwable failure) {
            safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                    "DEBIT_HOLD_PLACEMENT_OUTCOME_AMBIGUOUS: " + bounded(failure.getMessage()));
            throw failure;
        }
    }

    @Around("execution(* com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient.captureDebitHold(..)) "
            + "&& args(accountId,holdId,transactionId,reason)")
    public Object captureDebitHold(ProceedingJoinPoint joinPoint, String accountId, String holdId,
                                   String transactionId, String reason) throws Throwable {
        Optional<SpendingLimitReservationSagaContext.Context> active = sagaContext.current();
        if (active.isEmpty()) {
            return joinPoint.proceed();
        }
        SpendingLimitReservationSagaContext.Context context = active.get();
        markBeforeRemoteEffect(context, "DEBIT_CAPTURE_IN_FLIGHT");
        try {
            Object result = joinPoint.proceed();
            if (result instanceof ResilientAccountServiceClient.DebitHoldResponse response) {
                if (response.isApplied()) {
                    safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                            "DEBIT_CAPTURE_APPLIED_AWAITING_LOCAL_COMMIT");
                } else {
                    safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                            "DEBIT_CAPTURE_REJECTED_HOLD_REQUIRES_RELEASE");
                }
            }
            return result;
        } catch (Throwable failure) {
            safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                    "DEBIT_CAPTURE_OUTCOME_AMBIGUOUS: " + bounded(failure.getMessage()));
            throw failure;
        }
    }

    @Around("execution(* com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient.releaseDebitHold(..)) "
            + "&& args(accountId,holdId,transactionId,reason)")
    public Object releaseDebitHold(ProceedingJoinPoint joinPoint, String accountId, String holdId,
                                   String transactionId, String reason) throws Throwable {
        Optional<SpendingLimitReservationSagaContext.Context> active = sagaContext.current();
        if (active.isEmpty()) {
            return joinPoint.proceed();
        }
        SpendingLimitReservationSagaContext.Context context = active.get();
        markBeforeRemoteEffect(context, "DEBIT_HOLD_RELEASE_IN_FLIGHT");
        try {
            Object result = joinPoint.proceed();
            if (result instanceof ResilientAccountServiceClient.DebitHoldResponse response) {
                if (response.isApplied()) {
                    safeUpdate(context, TransactionIdempotencyClaimState.RESERVED,
                            "DEBIT_HOLD_RELEASED");
                } else {
                    safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                            "DEBIT_HOLD_RELEASE_NOT_CONFIRMED");
                }
            }
            return result;
        } catch (Throwable failure) {
            safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                    "DEBIT_HOLD_RELEASE_OUTCOME_AMBIGUOUS: " + bounded(failure.getMessage()));
            throw failure;
        }
    }

    @Around("execution(* com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient.applyBalanceOperation(..)) "
            + "&& args(accountId,operationId,delta,transactionId,reason,allowNegative)")
    public Object balanceOperation(ProceedingJoinPoint joinPoint, String accountId, String operationId,
                                   BigDecimal delta, String transactionId, String reason,
                                   boolean allowNegative) throws Throwable {
        Optional<SpendingLimitReservationSagaContext.Context> active = sagaContext.current();
        if (active.isEmpty() || (!TRANSFER_CREDIT.equals(reason) && !TRANSFER_COMPENSATION.equals(reason))) {
            return joinPoint.proceed();
        }
        SpendingLimitReservationSagaContext.Context context = active.get();
        if (TRANSFER_CREDIT.equals(reason)) {
            return transferCredit(joinPoint, context);
        }
        return transferCompensation(joinPoint, context);
    }

    private Object transferCredit(ProceedingJoinPoint joinPoint,
                                  SpendingLimitReservationSagaContext.Context context) throws Throwable {
        markBeforeRemoteEffect(context, "TRANSFER_CREDIT_IN_FLIGHT");
        final Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable failure) {
            safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                    "TRANSFER_CREDIT_OUTCOME_AMBIGUOUS: " + bounded(failure.getMessage()));
            throw failure;
        }
        if (!(result instanceof ResilientAccountServiceClient.BalanceOperationResponse response)) {
            safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                    "TRANSFER_CREDIT_RESPONSE_INVALID");
            throw new IllegalStateException("Account service returned no transfer credit result");
        }
        if (!response.isApplied()) {
            safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                    "TRANSFER_CREDIT_REJECTED_SOURCE_DEBIT_REQUIRES_COMPENSATION");
            throw new IllegalStateException(firstNonBlank(response.getMessage(), "Transfer credit was not applied"));
        }
        safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                "TRANSFER_CREDIT_APPLIED_AWAITING_LOCAL_COMMIT");
        return response;
    }

    private Object transferCompensation(ProceedingJoinPoint joinPoint,
                                        SpendingLimitReservationSagaContext.Context context) throws Throwable {
        TransactionIdempotencyClaim beforeCompensation =
                claimService.require(context.userId(), context.idempotencyKey());
        boolean destinationCreditRequiresReconciliation =
                destinationCreditRequiresReconciliation(beforeCompensation.getFailureDetails());
        markBeforeRemoteEffect(context, "TRANSFER_COMPENSATION_IN_FLIGHT");
        try {
            Object result = joinPoint.proceed();
            if (result instanceof ResilientAccountServiceClient.BalanceOperationResponse response
                    && response.isApplied()) {
                if (destinationCreditRequiresReconciliation) {
                    safeUpdate(context, TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                            "SOURCE_COMPENSATED_BUT_TRANSFER_CREDIT_REQUIRES_RECONCILIATION");
                } else {
                    safeUpdate(context, TransactionIdempotencyClaimState.RESERVED,
                            "TRANSFER_COMPENSATION_APPLIED");
                }
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

    private boolean destinationCreditRequiresReconciliation(String details) {
        if (details == null) {
            return false;
        }
        return details.equals("TRANSFER_CREDIT_IN_FLIGHT")
                || details.equals("TRANSFER_CREDIT_APPLIED_AWAITING_LOCAL_COMMIT")
                || details.equals("TRANSFER_CREDIT_RESPONSE_INVALID")
                || details.startsWith("TRANSFER_CREDIT_OUTCOME_AMBIGUOUS");
    }

    private void markBeforeRemoteEffect(SpendingLimitReservationSagaContext.Context context, String details) {
        claimService.updateState(context.userId(), context.idempotencyKey(),
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED, null, details);
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

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String bounded(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.length() <= 800 ? message : message.substring(0, 800);
    }
}
