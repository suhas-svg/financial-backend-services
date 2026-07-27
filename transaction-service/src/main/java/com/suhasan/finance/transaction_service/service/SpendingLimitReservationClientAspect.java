package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class SpendingLimitReservationClientAspect {
    private final TransactionIdempotencyClaimService claimService;
    private final SpendingLimitReservationLifecycleClient lifecycleClient;
    private final TransactionRepository transactionRepository;

    @Around("execution(* com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient.reserveSpendingLimit(..)) "
            + "&& args(accountId,operationType,amount,idempotencyKey,userId)")
    public Object reserve(ProceedingJoinPoint joinPoint, String accountId, String operationType,
                          BigDecimal amount, String idempotencyKey, String userId) throws Throwable {
        Optional<TransactionIdempotencyClaim> existing = claimService.find(userId, idempotencyKey);
        if (existing.isEmpty()) {
            return joinPoint.proceed();
        }
        TransactionIdempotencyClaim claim = existing.get();
        requireClaimMatchesCall(claim, accountId, operationType, amount);
        SpendingLimitReservationLifecycleClient.ReservationResponse response = lifecycleClient.reserve(
                accountId, operationType, amount, idempotencyKey, userId,
                claim.getCurrency(), claim.getClaimId());
        if (response == null) {
            throw new IllegalStateException("Account service returned no spending reservation response");
        }
        if (response.allowed()) {
            claimService.recordReservation(userId, idempotencyKey, response);
            if (!"RESERVED".equalsIgnoreCase(response.state())) {
                throw new IllegalStateException(
                        "Idempotency-Key refers to a reservation that is no longer active: " + response.state());
            }
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
        String correlation = reservationCorrelation(claim, reservation);

        Optional<Transaction> transaction = transactionRepository
                .findFirstByCreatedByAndTypeAndIdempotencyKey(
                        userId, claim.getTransactionType(), claim.getIdempotencyKey());
        if (transaction.isPresent()) {
            TransactionStatus status = transaction.get().getStatus();
            if (status == TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION
                    || status == TransactionStatus.PROCESSING
                    || status == TransactionStatus.PENDING) {
                SpendingLimitReservationLifecycleClient.ReservationResponse held = lifecycleClient.transition(
                        accountId, reservation.reservationId(), "reconciliation-required", userId,
                        correlation, "AMBIGUOUS_TRANSACTION_OUTCOME");
                claimService.updateState(userId, idempotencyKey,
                        TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                        held == null ? "RECONCILIATION_REQUIRED" : held.state(),
                        "Legacy release suppressed because the transaction outcome is ambiguous");
                return null;
            }
            if (status == TransactionStatus.COMPLETED || status == TransactionStatus.REVERSED) {
                SpendingLimitReservationLifecycleClient.ReservationResponse consumed = lifecycleClient.transition(
                        accountId, reservation.reservationId(), "consume", userId,
                        correlation, "TRANSACTION_COMPLETED");
                claimService.updateState(userId, idempotencyKey,
                        TransactionIdempotencyClaimState.COMPLETED,
                        consumed == null ? "CONSUMED" : consumed.state(), null);
                return null;
            }
        }

        SpendingLimitReservationLifecycleClient.ReservationResponse released = lifecycleClient.transition(
                accountId, reservation.reservationId(), "release", userId, correlation,
                "TRANSACTION_DEFINITIVELY_FAILED");
        claimService.updateState(userId, idempotencyKey,
                TransactionIdempotencyClaimState.RELEASED,
                released == null ? "RELEASED" : released.state(), null);
        return null;
    }

    private SpendingLimitReservationLifecycleClient.ReservationResponse ensureReservation(
            TransactionIdempotencyClaim claim) {
        if (claim.getReservationId() != null) {
            return new SpendingLimitReservationLifecycleClient.ReservationResponse(
                    true, true, claim.getReservationCurrency(), null, null, null, null,
                    claim.getReservationId(), firstNonBlank(claim.getReservationCorrelation(), claim.getClaimId()),
                    claim.getReservationAmount(), claim.getReservationFingerprint(), claim.getReservationState(),
                    null, null, claim.getExpiresAt(), null);
        }
        SpendingLimitReservationLifecycleClient.ReservationResponse response = lifecycleClient.lookup(
                claim.getAccountId(), claim.getOperationType(), claim.getIdempotencyKey(), claim.getUserId());
        if (response != null && response.reservationId() != null) {
            claimService.recordReservation(claim.getUserId(), claim.getIdempotencyKey(), response);
        }
        return response;
    }

    private void requireClaimMatchesCall(TransactionIdempotencyClaim claim, String accountId,
                                         String operationType, BigDecimal amount) {
        boolean amountMatches = claim.getAmount() != null && amount != null
                && claim.getAmount().compareTo(amount) == 0;
        if (!Objects.equals(claim.getAccountId(), accountId)
                || !Objects.equals(claim.getOperationType(), operationType)
                || !amountMatches) {
            throw new IllegalStateException(
                    "Durable transaction claim does not match the spending reservation request");
        }
    }

    private String reservationCorrelation(
            TransactionIdempotencyClaim claim,
            SpendingLimitReservationLifecycleClient.ReservationResponse reservation) {
        return firstNonBlank(reservation.transactionCorrelation(),
                firstNonBlank(claim.getReservationCorrelation(), claim.getClaimId()));
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
