package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpendingLimitReservationSagaCoordinator {
    private static final List<TransactionIdempotencyClaimState> RECONCILABLE_STATES = List.of(
            TransactionIdempotencyClaimState.CLAIMED,
            TransactionIdempotencyClaimState.RESERVED,
            TransactionIdempotencyClaimState.COMPLETED_PENDING_CONSUME,
            TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED);

    private final TransactionIdempotencyClaimService claimService;
    private final SpendingLimitReservationLifecycleClient lifecycleClient;
    private final TransactionRepository transactionRepository;

    public void completed(TransactionResponse response, String userId, String idempotencyKey) {
        if (response == null) {
            return;
        }
        claimService.recordTransaction(userId, idempotencyKey, response);
        if (response.getStatus() == TransactionStatus.COMPLETED
                || response.getStatus() == TransactionStatus.REVERSED) {
            reconcile(userId, response.getType(), idempotencyKey, false, null);
        } else if (response.getStatus() == TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION) {
            requireManualReconciliation(claimService.require(userId, idempotencyKey),
                    "TRANSACTION_REQUIRES_MANUAL_ACTION");
        }
    }

    public void failed(String userId, TransactionType type, String idempotencyKey, Throwable failure) {
        reconcile(userId, type, idempotencyKey, true,
                failure == null ? null : failure.getMessage());
    }

    @Scheduled(fixedDelayString = "${spending-limit.reconciliation-delay-ms:60000}")
    public void reconcileStaleClaims() {
        LocalDateTime now = LocalDateTime.now();
        for (TransactionIdempotencyClaim claim :
                claimService.staleClaims(RECONCILABLE_STATES, now.minusMinutes(1))) {
            try {
                reconcile(claim.getUserId(), claim.getTransactionType(),
                        claim.getIdempotencyKey(), false, "SCHEDULED_RECONCILIATION");
            } catch (RuntimeException error) {
                log.warn("Spending reservation reconciliation failed for claim {}: {}",
                        claim.getClaimId(), error.getMessage());
            }
        }
    }

    private void reconcile(String userId, TransactionType type, String idempotencyKey,
                           boolean immediateFailure, String failureDetails) {
        TransactionIdempotencyClaim claim = claimService.require(userId, idempotencyKey);
        SpendingLimitReservationLifecycleClient.ReservationResponse reservation;
        try {
            reservation = ensureReservation(claim);
        } catch (RuntimeException lookupFailure) {
            claimService.updateState(userId, idempotencyKey,
                    TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                    claim.getReservationState(), bounded(failureDetails, lookupFailure.getMessage()));
            return;
        }

        Optional<Transaction> transaction = transactionRepository
                .findFirstByCreatedByAndTypeAndIdempotencyKey(userId, type, idempotencyKey);
        if (transaction.isPresent()) {
            Transaction current = transaction.get();
            claimService.recordTransaction(userId, idempotencyKey,
                    TransactionResponse.builder()
                            .transactionId(current.getTransactionId())
                            .type(current.getType())
                            .status(current.getStatus())
                            .build());
            TransactionIdempotencyClaim refreshed = claimService.require(userId, idempotencyKey);
            if (current.getStatus() == TransactionStatus.COMPLETED
                    || current.getStatus() == TransactionStatus.REVERSED) {
                consume(refreshed, reservation, "TRANSACTION_COMPLETED");
                return;
            }
            if (current.getStatus() == TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION) {
                requireManualReconciliation(refreshed, "AMBIGUOUS_TRANSACTION_OUTCOME");
                return;
            }
            if (current.getStatus() == TransactionStatus.FAILED
                    || current.getStatus() == TransactionStatus.CANCELLED) {
                if (refreshed.getState() == TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED) {
                    requireManualReconciliation(refreshed,
                            firstNonBlank(refreshed.getFailureDetails(),
                                    "FAILED_TRANSACTION_WITH_AMBIGUOUS_REMOTE_DEBIT_OUTCOME"));
                } else {
                    release(refreshed, reservation, "TRANSACTION_DEFINITIVELY_FAILED");
                }
                return;
            }
            if (refreshed.getState() == TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED
                    || (refreshed.getExpiresAt() != null
                    && refreshed.getExpiresAt().isBefore(LocalDateTime.now()))) {
                requireManualReconciliation(refreshed,
                        firstNonBlank(refreshed.getFailureDetails(),
                                "PROCESSING_TRANSACTION_EXCEEDED_RESERVATION_LEASE"));
            }
            return;
        }

        if (claim.getState() == TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED) {
            requireManualReconciliation(claim,
                    firstNonBlank(claim.getFailureDetails(), "AMBIGUOUS_REMOTE_DEBIT_OUTCOME"));
            return;
        }

        if (reservation == null || reservation.reservationId() == null) {
            if (immediateFailure || isExpired(claim)) {
                claimService.updateState(userId, idempotencyKey,
                        TransactionIdempotencyClaimState.CLOSED_NO_RESERVATION,
                        null, bounded(failureDetails, "NO_REMOTE_RESERVATION_FOUND"));
            }
            return;
        }

        if (immediateFailure || isExpired(claim)) {
            release(claimService.require(userId, idempotencyKey), reservation,
                    "LOCAL_TRANSACTION_NOT_CREATED");
        }
    }

    private boolean isExpired(TransactionIdempotencyClaim claim) {
        return claim.getExpiresAt() != null && claim.getExpiresAt().isBefore(LocalDateTime.now());
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

    private void consume(TransactionIdempotencyClaim claim,
                         SpendingLimitReservationLifecycleClient.ReservationResponse reservation,
                         String outcome) {
        if (reservation == null || reservation.reservationId() == null) {
            requireManualReconciliation(claim, "COMPLETED_TRANSACTION_HAS_NO_RESERVATION_IDENTITY");
            return;
        }
        try {
            SpendingLimitReservationLifecycleClient.ReservationResponse consumed = lifecycleClient.transition(
                    claim.getAccountId(), reservation.reservationId(), "consume",
                    claim.getUserId(), reservationCorrelation(claim, reservation), outcome);
            claimService.updateState(claim.getUserId(), claim.getIdempotencyKey(),
                    TransactionIdempotencyClaimState.COMPLETED,
                    consumed == null ? "CONSUMED" : consumed.state(), null);
        } catch (RuntimeException error) {
            claimService.updateState(claim.getUserId(), claim.getIdempotencyKey(),
                    TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                    reservation.state(), bounded(outcome, error.getMessage()));
        }
    }

    private void release(TransactionIdempotencyClaim claim,
                         SpendingLimitReservationLifecycleClient.ReservationResponse reservation,
                         String outcome) {
        if (claim.getState() == TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED) {
            requireManualReconciliation(claim,
                    firstNonBlank(claim.getFailureDetails(), "AMBIGUOUS_REMOTE_DEBIT_OUTCOME"));
            return;
        }
        if (reservation == null || reservation.reservationId() == null) {
            claimService.updateState(claim.getUserId(), claim.getIdempotencyKey(),
                    TransactionIdempotencyClaimState.CLOSED_NO_RESERVATION,
                    null, outcome);
            return;
        }
        try {
            SpendingLimitReservationLifecycleClient.ReservationResponse released = lifecycleClient.transition(
                    claim.getAccountId(), reservation.reservationId(), "release",
                    claim.getUserId(), reservationCorrelation(claim, reservation), outcome);
            claimService.updateState(claim.getUserId(), claim.getIdempotencyKey(),
                    TransactionIdempotencyClaimState.RELEASED,
                    released == null ? "RELEASED" : released.state(), null);
        } catch (IllegalStateException ambiguous) {
            requireManualReconciliation(claim, bounded(outcome, ambiguous.getMessage()));
        } catch (RuntimeException unavailable) {
            claimService.updateState(claim.getUserId(), claim.getIdempotencyKey(),
                    TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                    reservation.state(), bounded(outcome, unavailable.getMessage()));
        }
    }

    private void requireManualReconciliation(TransactionIdempotencyClaim claim, String outcome) {
        SpendingLimitReservationLifecycleClient.ReservationResponse reservation = null;
        String persistedOutcome = outcome;
        try {
            reservation = ensureReservation(claim);
            if (reservation != null && reservation.reservationId() != null) {
                try {
                    SpendingLimitReservationLifecycleClient.ReservationResponse marked =
                            lifecycleClient.transition(claim.getAccountId(), reservation.reservationId(),
                                    "reconciliation-required", claim.getUserId(),
                                    reservationCorrelation(claim, reservation), outcome);
                    if (marked != null) {
                        reservation = marked;
                    }
                } catch (RuntimeException transitionFailure) {
                    persistedOutcome = bounded(outcome, transitionFailure.getMessage());
                    log.warn("Failed to mark reservation {} for manual reconciliation: {}",
                            reservation.reservationId(), transitionFailure.getMessage());
                }
            }
        } catch (RuntimeException lookupFailure) {
            persistedOutcome = bounded(outcome, lookupFailure.getMessage());
            log.warn("Failed to look up reservation for manual reconciliation claim {}: {}",
                    claim.getClaimId(), lookupFailure.getMessage());
        }
        claimService.updateState(claim.getUserId(), claim.getIdempotencyKey(),
                TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED,
                reservation == null ? claim.getReservationState() : reservation.state(), persistedOutcome);
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

    private String bounded(String first, String second) {
        String joined = (first == null || first.isBlank()) ? second
                : (second == null || second.isBlank()) ? first : first + ": " + second;
        if (joined == null) {
            return null;
        }
        return joined.length() <= 1000 ? joined : joined.substring(0, 1000);
    }
}
