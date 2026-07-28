package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionIdempotencyClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionIdempotencyClaimService {
    private final TransactionIdempotencyClaimRepository claims;
    private final PlatformTransactionManager transactionManager;

    @Value("${spending-limit.claim-lease-minutes:30}")
    private long claimLeaseMinutes;

    public TransactionIdempotencyClaim claimTransfer(
            TransferRequest request, String userId, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        String fingerprint = canonicalFingerprint("TRANSFER", userId,
                request.getFromAccountId(), request.getToAccountId(), request.getBeneficiaryId(),
                request.getAmount(), request.getCurrency(), request.getDescription(), request.getReference());
        return claim(userId, TransactionType.TRANSFER, key, fingerprint,
                request.getFromAccountId(), "TRANSFER", request.getAmount(), request.getCurrency());
    }

    public TransactionIdempotencyClaim claimWithdrawal(
            String accountId, BigDecimal amount, String description, String reference,
            String userId, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        String fingerprint = canonicalFingerprint("WITHDRAWAL", userId, accountId,
                amount, description, reference);
        return claim(userId, TransactionType.WITHDRAWAL, key, fingerprint,
                accountId, "WITHDRAWAL", amount, null);
    }

    public TransactionIdempotencyClaim claimWithdrawalRequest(
            String accountId, BigDecimal amount, String currency, String description, String reference,
            String userId, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        String normalizedCurrency = normalizeCurrency(currency);
        String fingerprint = canonicalFingerprint("WITHDRAWAL", userId, accountId,
                amount, normalizedCurrency, description, reference);
        return claim(userId, TransactionType.WITHDRAWAL, key, fingerprint,
                accountId, "WITHDRAWAL", amount, normalizedCurrency);
    }

    public Optional<TransactionIdempotencyClaim> find(String userId, String idempotencyKey) {
        if (userId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return claims.findByUserIdAndIdempotencyKey(userId, normalizeKey(idempotencyKey));
    }

    public TransactionIdempotencyClaim require(String userId, String idempotencyKey) {
        return find(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Durable transaction idempotency claim is missing"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionIdempotencyClaim recordReservation(
            String userId, String idempotencyKey,
            SpendingLimitReservationLifecycleClient.ReservationResponse response) {
        TransactionIdempotencyClaim claim = claims.lockByScope(userId, normalizeKey(idempotencyKey))
                .orElseThrow(() -> new IllegalStateException("Durable transaction idempotency claim is missing"));
        validateReservationResponse(claim, response);

        String reservationState = response.state().trim().toUpperCase(Locale.ROOT);
        claim.setReservationId(response.reservationId());
        claim.setReservationCorrelation(firstNonBlank(response.transactionCorrelation(), claim.getClaimId()));
        claim.setReservationFingerprint(response.fingerprint());
        claim.setReservationAmount(response.amount());
        claim.setReservationCurrency(normalizeCurrency(response.currency()));
        claim.setReservationState(reservationState);
        claim.setState(localStateForReservation(reservationState));
        claim.setFailureDetails("RESERVED".equals(reservationState)
                ? null : "Remote reservation returned lifecycle state " + reservationState);
        claim.setUpdatedAt(LocalDateTime.now());
        if (response.expiresAt() != null) {
            claim.setExpiresAt(response.expiresAt());
        }
        return claims.save(claim);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionIdempotencyClaim recordTransaction(
            String userId, String idempotencyKey, TransactionResponse response) {
        TransactionIdempotencyClaim claim = claims.lockByScope(userId, normalizeKey(idempotencyKey))
                .orElseThrow(() -> new IllegalStateException("Durable transaction idempotency claim is missing"));
        if (response != null) {
            claim.setTransactionId(response.getTransactionId());
            if (response.getStatus() == TransactionStatus.COMPLETED
                    || response.getStatus() == TransactionStatus.REVERSED) {
                claim.setState(TransactionIdempotencyClaimState.COMPLETED_PENDING_CONSUME);
            } else if (response.getStatus() == TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION) {
                claim.setState(TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED);
            }
        }
        claim.setUpdatedAt(LocalDateTime.now());
        return claims.save(claim);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionIdempotencyClaim updateState(
            String userId, String idempotencyKey, TransactionIdempotencyClaimState state,
            String reservationState, String details) {
        TransactionIdempotencyClaim claim = claims.lockByScope(userId, normalizeKey(idempotencyKey))
                .orElseThrow(() -> new IllegalStateException("Durable transaction idempotency claim is missing"));
        claim.setState(state);
        if (reservationState != null) {
            claim.setReservationState(reservationState);
        }
        claim.setFailureDetails(details);
        claim.setUpdatedAt(LocalDateTime.now());
        return claims.save(claim);
    }

    @Transactional(readOnly = true)
    public List<TransactionIdempotencyClaim> staleClaims(
            Collection<TransactionIdempotencyClaimState> states, LocalDateTime cutoff) {
        return claims.findTop100ByStateInAndUpdatedAtBeforeOrderByUpdatedAtAsc(states, cutoff);
    }

    private TransactionIdempotencyClaim claim(
            String userId, TransactionType transactionType, String idempotencyKey,
            String fingerprint, String accountId, String operationType,
            BigDecimal amount, String currency) {
        Optional<TransactionIdempotencyClaim> existing =
                claims.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            requireMatchingClaim(existing.get(), transactionType, fingerprint, accountId,
                    operationType, amount, currency);
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.now();
        TransactionIdempotencyClaim candidate = TransactionIdempotencyClaim.builder()
                .claimId(UUID.randomUUID().toString())
                .userId(userId)
                .transactionType(transactionType)
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint)
                .accountId(accountId)
                .operationType(operationType)
                .amount(amount)
                .currency(normalizeCurrency(currency))
                .state(TransactionIdempotencyClaimState.CLAIMED)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusMinutes(leaseMinutes()))
                .build();

        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return requiresNew.execute(status -> claims.saveAndFlush(candidate));
        } catch (DataIntegrityViolationException race) {
            TransactionIdempotencyClaim winner = requiresNew.execute(status ->
                    claims.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Idempotency claim conflict but winner was not visible", race)));
            requireMatchingClaim(winner, transactionType, fingerprint, accountId,
                    operationType, amount, currency);
            return winner;
        }
    }

    private void requireMatchingClaim(
            TransactionIdempotencyClaim existing, TransactionType transactionType,
            String fingerprint, String accountId, String operationType,
            BigDecimal amount, String currency) {
        boolean amountMatches = existing.getAmount() != null && amount != null
                && existing.getAmount().compareTo(amount) == 0;
        boolean currencyMatches = Objects.equals(
                normalizeCurrency(existing.getCurrency()), normalizeCurrency(currency));
        if (existing.getTransactionType() != transactionType
                || !Objects.equals(existing.getRequestFingerprint(), fingerprint)
                || !Objects.equals(existing.getAccountId(), accountId)
                || !Objects.equals(existing.getOperationType(), operationType)
                || !amountMatches
                || !currencyMatches) {
            throw new IllegalStateException(
                    "Idempotency-Key was reused with a different transaction or reservation payload");
        }
    }

    private void validateReservationResponse(
            TransactionIdempotencyClaim claim,
            SpendingLimitReservationLifecycleClient.ReservationResponse response) {
        if (response == null || !response.allowed()) {
            throw new IllegalStateException("Account service returned an unusable spending reservation response");
        }
        if (response.reservationId() == null || response.amount() == null
                || response.currency() == null || response.currency().isBlank()
                || response.fingerprint() == null || response.fingerprint().isBlank()
                || response.state() == null || response.state().isBlank()) {
            throw new IllegalStateException(
                    "Account service omitted required spending reservation identity or payload fields");
        }
        if (claim.getAmount() == null || claim.getAmount().compareTo(response.amount()) != 0) {
            throw new IllegalStateException("Account service returned a different reservation amount");
        }
        String responseCurrency = normalizeCurrency(response.currency());
        if (claim.getCurrency() != null
                && !Objects.equals(normalizeCurrency(claim.getCurrency()), responseCurrency)) {
            throw new IllegalStateException("Account service returned a different reservation currency");
        }
        String expectedFingerprint = reservationFingerprint(
                claim.getAccountId(), claim.getUserId(), claim.getOperationType(),
                response.amount(), responseCurrency, claim.getIdempotencyKey());
        if (!Objects.equals(expectedFingerprint, response.fingerprint())) {
            throw new IllegalStateException("Account service returned a non-canonical reservation fingerprint");
        }
    }

    private TransactionIdempotencyClaimState localStateForReservation(String reservationState) {
        return switch (reservationState) {
            case "RESERVED" -> TransactionIdempotencyClaimState.RESERVED;
            case "RELEASED" -> TransactionIdempotencyClaimState.RELEASED;
            case "CONSUMED", "EXPIRED", "RECONCILIATION_REQUIRED" ->
                    TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED;
            default -> TransactionIdempotencyClaimState.RECONCILIATION_REQUIRED;
        };
    }

    private String reservationFingerprint(String accountId, String userId, String operationType,
                                          BigDecimal amount, String currency, String idempotencyKey) {
        return canonicalFingerprint(canonicalAccountId(accountId), userId.trim(),
                operationType.trim().toUpperCase(Locale.ROOT),
                amount.stripTrailingZeros().toPlainString(), currency, normalizeKey(idempotencyKey));
    }

    private String canonicalAccountId(String accountId) {
        String normalized = accountId == null ? "" : accountId.trim();
        try {
            return new BigInteger(normalized).toString();
        } catch (NumberFormatException notNumeric) {
            return normalized;
        }
    }

    private String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for spending operations");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 128 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Idempotency-Key must be 1-128 URL-safe characters");
        }
        return normalized;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return null;
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String canonicalFingerprint(Object... parts) {
        String canonical = Arrays.stream(parts)
                .map(this::canonicalPart)
                .collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String canonicalPart(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return value.toString().trim();
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long leaseMinutes() {
        return claimLeaseMinutes > 0 ? claimLeaseMinutes : 30;
    }
}
