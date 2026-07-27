package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
import lombok.RequiredArgsConstructor;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class SpendingLimitReservationClientAspect {
    private final TransactionIdempotencyClaimService claimService;
    private final SpendingLimitReservationLifecycleClient lifecycleClient;

    @Around("execution(* com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient.reserveSpendingLimit(..)) "
            + "&& args(accountId,operationType,amount,idempotencyKey,userId)")
    public Object reserve(ProceedingJoinPoint joinPoint, String accountId, String operationType,
                          BigDecimal amount, String idempotencyKey, String userId) throws Throwable {
        Optional<TransactionIdempotencyClaim> existing = claimService.find(userId, idempotencyKey);
        if (existing.isEmpty()) {
            return joinPoint.proceed();
        }
        TransactionIdempotencyClaim claim = existing.get();
        SpendingLimitReservationLifecycleClient.ReservationResponse response = lifecycleClient.reserve(
                accountId, operationType, amount, idempotencyKey, userId,
                claim.getCurrency(), claim.getClaimId());
        if (response != null && response.allowed() && response.reservationId() != null) {
            claimService.recordReservation(userId, idempotencyKey, response);
        }
        if (response == null) {
            throw new IllegalStateException("Account service returned no spending reservation response");
        }
        return new ResilientAccountServiceClient.SpendingLimitReservationResponse(
                response.allowed(), response.replay(), response.dailyLimit(), response.currency(),
                response.dailyUsed(), response.remaining(), response.reason());
    }

    @Around("execution(* com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient.releaseSpendingLimit(..)) "
            + "&& args(accountId,operationType,idempotencyKey,userId)")
    public Object release(ProceedingJoinPoint joinPoint, String accountId, String operationType,
                          String idempotencyKey, String userId) throws Throwable {
        Optional<TransactionIdempotencyClaim> existing = claimService.find(userId, idempotencyKey);
        if (existing.isEmpty()) {
            return joinPoint.proceed();
        }
        TransactionIdempotencyClaim claim = existing.get();
        SpendingLimitReservationLifecycleClient.ReservationResponse reservation = ensureReservation(claim);
        if (reservation == null || reservation.reservationId() == null) {
            return null;
        }
        SpendingLimitReservationLifecycleClient.ReservationResponse released = lifecycleClient.transition(
                accountId, reservation.reservationId(), "release", userId, claim.getClaimId(),
                "TRANSACTION_DEFINITIVELY_FAILED");
        claimService.updateState(userId, idempotencyKey,
                com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState.RELEASED,
                released == null ? "RELEASED" : released.state(), null);
        return null;
    }

    private SpendingLimitReservationLifecycleClient.ReservationResponse ensureReservation(
            TransactionIdempotencyClaim claim) {
        if (claim.getReservationId() != null) {
            return new SpendingLimitReservationLifecycleClient.ReservationResponse(
                    true, true, claim.getReservationCurrency(), null, null, null, null,
                    claim.getReservationId(), claim.getClaimId(), claim.getReservationAmount(),
                    claim.getReservationFingerprint(), claim.getReservationState(),
                    null, null, claim.getExpiresAt(), null);
        }
        SpendingLimitReservationLifecycleClient.ReservationResponse response = lifecycleClient.lookup(
                claim.getAccountId(), claim.getOperationType(), claim.getIdempotencyKey(), claim.getUserId());
        if (response != null && response.reservationId() != null) {
            claimService.recordReservation(claim.getUserId(), claim.getIdempotencyKey(), response);
        }
        return response;
    }
}
