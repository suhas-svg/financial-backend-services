package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.NotificationCreateRequest;
import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationType;
import com.suhasan.finance.account_service.dto.SpendingLimitDtos;
import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.AccountSpendingLimit;
import com.suhasan.finance.account_service.entity.SpendingLimitAuditEvent;
import com.suhasan.finance.account_service.entity.SpendingLimitReservation;
import com.suhasan.finance.account_service.entity.SpendingLimitReservationState;
import com.suhasan.finance.account_service.repository.AccountRepository;
import com.suhasan.finance.account_service.repository.AccountSpendingLimitRepository;
import com.suhasan.finance.account_service.repository.SpendingLimitAuditEventRepository;
import com.suhasan.finance.account_service.repository.SpendingLimitReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class SpendingLimitService {
    private static final String TRANSFER = "TRANSFER";
    private static final String WITHDRAWAL = "WITHDRAWAL";

    private final AccountRepository accounts;
    private final AccountSpendingLimitRepository limits;
    private final SpendingLimitReservationRepository reservations;
    private final SpendingLimitAuditEventRepository audits;
    private final MfaService mfa;
    private final NotificationService notifications;

    @Value("${spending-limits.reservation-lease-minutes:30}")
    private long reservationLeaseMinutes;

    @Transactional(readOnly = true)
    public List<SpendingLimitDtos.LimitResponse> list(final String user) {
        return accounts.findAll().stream()
                .filter(account -> user.equals(account.getOwnerId()))
                .map(account -> view(account, limits.findById(account.getId()).orElse(null)))
                .toList();
    }

    @Transactional
    public SpendingLimitDtos.LimitResponse update(final Long accountId, final String user,
                                                   final SpendingLimitDtos.UpdateRequest request) {
        final Account account = accounts.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!user.equals(account.getOwnerId())) {
            throw new AccessDeniedException("Account ownership required");
        }
        final AccountSpendingLimit limit = lockedOrCreate(accountId, user);
        applyDue(limit);
        final boolean transferIncrease = request.transferDailyLimit().compareTo(limit.getTransferDailyLimit()) > 0;
        final boolean withdrawalIncrease = request.withdrawalDailyLimit().compareTo(limit.getWithdrawalDailyLimit()) > 0;
        final boolean increase = transferIncrease || withdrawalIncrease;
        if (increase) {
            if (request.credential() == null || request.credential().isBlank()
                    || !mfa.verifyCredential(mfa.activeMethod(user), request.credential())) {
                throw new IllegalArgumentException("Valid MFA credential required for limit increases");
            }
            if (!transferIncrease) {
                limit.setTransferDailyLimit(request.transferDailyLimit());
            }
            if (!withdrawalIncrease) {
                limit.setWithdrawalDailyLimit(request.withdrawalDailyLimit());
            }
            limit.setPendingTransferDailyLimit(
                    transferIncrease ? request.transferDailyLimit() : limit.getTransferDailyLimit());
            limit.setPendingWithdrawalDailyLimit(
                    withdrawalIncrease ? request.withdrawalDailyLimit() : limit.getWithdrawalDailyLimit());
            limit.setPendingEffectiveAt(LocalDateTime.now().plusHours(24));
            audit(accountId, user, "LIMIT_INCREASE_SCHEDULED", null, null, null, null,
                    "Increases verified and cooling; any reductions applied immediately");
        } else {
            limit.setTransferDailyLimit(request.transferDailyLimit());
            limit.setWithdrawalDailyLimit(request.withdrawalDailyLimit());
            limit.setPendingTransferDailyLimit(null);
            limit.setPendingWithdrawalDailyLimit(null);
            limit.setPendingEffectiveAt(null);
            audit(accountId, user, "LIMIT_REDUCED", null, null, null, null,
                    "Reduction applied immediately");
        }
        limit.setUpdatedAt(LocalDateTime.now());
        limit.setUpdatedBy(user);
        limits.save(limit);
        notify(user, accountId, increase ? "Limit increase scheduled" : "Spending limits updated",
                increase ? "Your verified increase will take effect after the 24-hour cooling period."
                        : "Your lower limits are effective immediately.",
                "limit-change-" + accountId + "-" + limit.getUpdatedAt());
        return view(account, limit);
    }

    @Transactional
    public SpendingLimitDtos.ReserveResponse reserve(final Long accountId,
                                                      final SpendingLimitDtos.ReserveRequest request) {
        final Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        final String operationType = normalizeOperation(request.operationType());
        final BigDecimal amount = normalizeAmount(request.amount());
        final String idempotencyKey = normalizeKey(request.idempotencyKey());
        final String requestedCurrency = normalizeCurrency(
                request.currency() == null || request.currency().isBlank() ? account.getCurrency() : request.currency());
        final String ownerId = request.userId().trim();
        final String fingerprint = fingerprint(accountId, ownerId, operationType, amount,
                requestedCurrency, idempotencyKey);

        final List<SpendingLimitReservation> scoped =
                reservations.findByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(accountId, idempotencyKey);
        if (scoped.size() > 1) {
            scoped.forEach(reservation -> markReconciliationRequired(
                    reservation, "AMBIGUOUS_LEGACY_IDEMPOTENCY_SCOPE"));
            throw new IllegalStateException(
                    "Idempotency-Key resolves to multiple legacy reservations; reconciliation required");
        }
        if (!scoped.isEmpty()) {
            final SpendingLimitReservation existing = hydrateLegacy(scoped.getFirst(), account);
            requireMatchingPayload(existing, accountId, ownerId, operationType, amount,
                    requestedCurrency, idempotencyKey, fingerprint);
            return reservationResponse(existing, true, account, lockedOrCreate(accountId, ownerId), null);
        }

        if (!Objects.equals(account.getOwnerId(), ownerId)) {
            throw new AccessDeniedException("Account ownership required");
        }
        final String accountCurrency = normalizeCurrency(account.getCurrency());
        if (!accountCurrency.equals(requestedCurrency)) {
            throw new IllegalArgumentException("Reservation currency must match the account currency");
        }

        final AccountSpendingLimit limit = lockedOrCreate(accountId, ownerId);
        applyDue(limit);
        final BigDecimal dailyLimit = limitFor(limit, operationType);
        final BigDecimal used = reservations.used(accountId, operationType, LocalDate.now());
        final BigDecimal projected = used.add(amount);
        if (projected.compareTo(dailyLimit) > 0) {
            audit(accountId, ownerId, "LIMIT_REJECTED", operationType, amount, dailyLimit, used,
                    "Daily spending limit exceeded");
            notify(ownerId, accountId, "Operation rejected",
                    "A " + operationType.toLowerCase(Locale.ROOT)
                            + " was rejected because it exceeds your daily limit.",
                    "limit-reject-" + accountId + "-" + operationType + "-" + idempotencyKey);
            return new SpendingLimitDtos.ReserveResponse(false, false, accountCurrency, dailyLimit,
                    used, dailyLimit.subtract(used).max(BigDecimal.ZERO), "Daily spending limit exceeded");
        }

        final LocalDateTime now = LocalDateTime.now();
        final SpendingLimitReservation reservation = SpendingLimitReservation.builder()
                .accountId(accountId)
                .ownerId(ownerId)
                .operationType(operationType)
                .amount(amount)
                .currency(accountCurrency)
                .usageDate(LocalDate.now())
                .idempotencyKey(idempotencyKey)
                .fingerprint(fingerprint)
                .transactionCorrelation(firstNonBlank(request.transactionCorrelation(), idempotencyKey))
                .state(SpendingLimitReservationState.RESERVED)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusMinutes(leaseMinutes()))
                .build();
        final SpendingLimitReservation saved;
        try {
            saved = reservations.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException race) {
            final SpendingLimitReservation winner = reservations
                    .findFirstByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(accountId, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Reservation idempotency conflict but winner was not visible", race));
            final SpendingLimitReservation hydrated = hydrateLegacy(winner, account);
            requireMatchingPayload(hydrated, accountId, ownerId, operationType, amount,
                    requestedCurrency, idempotencyKey, fingerprint);
            return reservationResponse(hydrated, true, account, limit, null);
        }

        audit(accountId, ownerId,
                projected.compareTo(dailyLimit.multiply(new BigDecimal("0.80"))) >= 0
                        ? "LIMIT_APPROACHING" : "LIMIT_ENFORCED",
                operationType, amount, dailyLimit, projected,
                "Daily usage reserved before debit processing");
        if (projected.compareTo(dailyLimit.multiply(new BigDecimal("0.80"))) >= 0) {
            notify(ownerId, accountId, "Approaching daily limit",
                    "Your " + operationType.toLowerCase(Locale.ROOT)
                            + " usage has reached at least 80% of today's limit.",
                    "limit-near-" + accountId + "-" + operationType + "-" + LocalDate.now());
        }
        return reservationResponse(saved, false, account, limit, null);
    }

    @Transactional
    public SpendingLimitDtos.ReserveResponse lookup(final Long accountId, final String operationType,
                                                     final String idempotencyKey, final String userId) {
        final Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        final SpendingLimitReservation reservation = reservations
                .findFirstByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(accountId, normalizeKey(idempotencyKey))
                .orElseThrow(() -> new IllegalArgumentException("Spending limit reservation not found"));
        final SpendingLimitReservation hydrated = hydrateLegacy(reservation, account);
        if (!normalizeOperation(operationType).equals(hydrated.getOperationType())
                || !userId.trim().equals(hydrated.getOwnerId())) {
            throw new IllegalStateException("Reservation lookup payload does not match the original reservation");
        }
        return reservationResponse(hydrated, true, account, lockedOrCreate(accountId, userId), null);
    }

    @Transactional
    public SpendingLimitDtos.ReserveResponse consume(final Long accountId, final Long reservationId,
                                                      final SpendingLimitDtos.ReservationTransitionRequest request) {
        return transition(accountId, reservationId, request, SpendingLimitReservationState.CONSUMED);
    }

    @Transactional
    public SpendingLimitDtos.ReserveResponse release(final Long accountId, final Long reservationId,
                                                      final SpendingLimitDtos.ReservationTransitionRequest request) {
        return transition(accountId, reservationId, request, SpendingLimitReservationState.RELEASED);
    }

    @Transactional
    public SpendingLimitDtos.ReserveResponse requireReconciliation(
            final Long accountId, final Long reservationId,
            final SpendingLimitDtos.ReservationTransitionRequest request) {
        return transition(accountId, reservationId, request,
                SpendingLimitReservationState.RECONCILIATION_REQUIRED);
    }

    @Transactional
    public boolean release(final Long accountId, final String operationType,
                           final String idempotencyKey, final String userId) {
        final Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        final SpendingLimitReservation reservation = reservations
                .findFirstByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(accountId, normalizeKey(idempotencyKey))
                .orElse(null);
        if (reservation == null) {
            return false;
        }
        final SpendingLimitReservation hydrated = hydrateLegacy(reservation, account);
        if (!normalizeOperation(operationType).equals(hydrated.getOperationType())
                || !userId.trim().equals(hydrated.getOwnerId())) {
            throw new IllegalStateException("Release payload does not match the original reservation");
        }
        if (hydrated.getState() == SpendingLimitReservationState.RELEASED) {
            return false;
        }
        transitionLocked(hydrated, SpendingLimitReservationState.RELEASED,
                hydrated.getTransactionCorrelation(), "DOWNSTREAM_DEFINITIVE_FAILURE");
        return true;
    }

    @Scheduled(fixedDelayString = "${spending-limits.reservation-expiry-delay-ms:60000}")
    @Transactional
    public void reconcileExpiredLeases() {
        final LocalDateTime now = LocalDateTime.now();
        for (SpendingLimitReservation reservation : reservations
                .findTop100ByStateAndExpiresAtBeforeOrderByExpiresAtAsc(
                        SpendingLimitReservationState.RESERVED, now)) {
            if (reservation.getTransactionCorrelation() == null
                    || reservation.getTransactionCorrelation().isBlank()) {
                transitionLocked(reservation, SpendingLimitReservationState.EXPIRED,
                        null, "UNREFERENCED_RESERVATION_LEASE_EXPIRED");
            } else {
                markReconciliationRequired(reservation,
                        "LEASE_EXPIRED_PENDING_TRANSACTION_RECONCILIATION");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<SpendingLimitDtos.AuditResponse> auditEvents() {
        return audits.findAll().stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .limit(200)
                .map(event -> new SpendingLimitDtos.AuditResponse(event.getEventId(), event.getAccountId(),
                        event.getUserId(), event.getEventType(), event.getOperationType(), event.getAmount(),
                        event.getDailyLimit(), event.getDailyUsed(), event.getDetails(), event.getCreatedAt()))
                .toList();
    }

    private SpendingLimitDtos.ReserveResponse transition(
            final Long accountId, final Long reservationId,
            final SpendingLimitDtos.ReservationTransitionRequest request,
            final SpendingLimitReservationState target) {
        final Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        final SpendingLimitReservation reservation = reservations
                .lockByReservationIdAndAccountId(reservationId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Spending limit reservation not found"));
        final SpendingLimitReservation hydrated = hydrateLegacy(reservation, account);
        if (!request.userId().trim().equals(hydrated.getOwnerId())) {
            throw new IllegalStateException("Reservation owner does not match the original payload");
        }
        if (request.transactionCorrelation() != null && !request.transactionCorrelation().isBlank()
                && !Objects.equals(request.transactionCorrelation(), hydrated.getTransactionCorrelation())) {
            throw new IllegalStateException("Reservation transaction correlation does not match");
        }
        transitionLocked(hydrated, target, request.transactionCorrelation(), request.outcome());
        return reservationResponse(hydrated, true, account,
                lockedOrCreate(accountId, hydrated.getOwnerId()), null);
    }

    private void transitionLocked(final SpendingLimitReservation reservation,
                                  final SpendingLimitReservationState target,
                                  final String correlation, final String outcome) {
        final SpendingLimitReservationState current = reservation.getState();
        if (current == target) {
            return;
        }
        if (current == SpendingLimitReservationState.CONSUMED
                && target != SpendingLimitReservationState.RECONCILIATION_REQUIRED) {
            throw new IllegalStateException("Consumed reservation cannot transition to " + target);
        }
        if (current == SpendingLimitReservationState.RELEASED
                && target == SpendingLimitReservationState.CONSUMED) {
            markReconciliationRequired(reservation,
                    "COMPLETED_TRANSACTION_FOUND_AFTER_RESERVATION_RELEASE");
            throw new IllegalStateException("Released reservation cannot be consumed without reconciliation");
        }
        if (correlation != null && !correlation.isBlank()) {
            reservation.setTransactionCorrelation(correlation);
        }
        reservation.setState(target);
        reservation.setOutcome(firstNonBlank(outcome, target.name()));
        reservation.setOutcomeAt(LocalDateTime.now());
        reservation.setUpdatedAt(LocalDateTime.now());
        reservations.save(reservation);
        audit(reservation.getAccountId(), reservation.getOwnerId(),
                "LIMIT_RESERVATION_" + target.name(), reservation.getOperationType(),
                reservation.getAmount(), null, null, reservation.getOutcome());
    }

    private void markReconciliationRequired(final SpendingLimitReservation reservation, final String outcome) {
        reservation.setState(SpendingLimitReservationState.RECONCILIATION_REQUIRED);
        reservation.setOutcome(outcome);
        reservation.setOutcomeAt(LocalDateTime.now());
        reservation.setUpdatedAt(LocalDateTime.now());
        reservations.save(reservation);
    }

    private SpendingLimitReservation hydrateLegacy(final SpendingLimitReservation reservation,
                                                    final Account account) {
        boolean changed = false;
        if (reservation.getOwnerId() == null || reservation.getOwnerId().isBlank()) {
            reservation.setOwnerId(account.getOwnerId());
            changed = true;
        }
        if (reservation.getCurrency() == null || reservation.getCurrency().isBlank()) {
            reservation.setCurrency(normalizeCurrency(account.getCurrency()));
            changed = true;
        }
        if (reservation.getState() == null) {
            reservation.setState(SpendingLimitReservationState.RESERVED);
            changed = true;
        }
        if (reservation.getUpdatedAt() == null) {
            reservation.setUpdatedAt(reservation.getCreatedAt() == null
                    ? LocalDateTime.now() : reservation.getCreatedAt());
            changed = true;
        }
        if (reservation.getExpiresAt() == null) {
            reservation.setExpiresAt(reservation.getUpdatedAt().plusMinutes(leaseMinutes()));
            changed = true;
        }
        if (reservation.getTransactionCorrelation() == null
                || reservation.getTransactionCorrelation().isBlank()) {
            reservation.setTransactionCorrelation(reservation.getIdempotencyKey());
            changed = true;
        }
        if (reservation.getFingerprint() == null || reservation.getFingerprint().isBlank()) {
            reservation.setFingerprint(fingerprint(reservation.getAccountId(), reservation.getOwnerId(),
                    normalizeOperation(reservation.getOperationType()), normalizeAmount(reservation.getAmount()),
                    normalizeCurrency(reservation.getCurrency()), normalizeKey(reservation.getIdempotencyKey())));
            changed = true;
        }
        return changed ? reservations.save(reservation) : reservation;
    }

    private void requireMatchingPayload(final SpendingLimitReservation existing, final Long accountId,
                                        final String ownerId, final String operationType,
                                        final BigDecimal amount, final String currency,
                                        final String idempotencyKey, final String expectedFingerprint) {
        final boolean matches = Objects.equals(existing.getAccountId(), accountId)
                && Objects.equals(existing.getOwnerId(), ownerId)
                && Objects.equals(existing.getOperationType(), operationType)
                && existing.getAmount() != null && existing.getAmount().compareTo(amount) == 0
                && Objects.equals(normalizeCurrency(existing.getCurrency()), currency)
                && Objects.equals(existing.getIdempotencyKey(), idempotencyKey)
                && Objects.equals(existing.getFingerprint(), expectedFingerprint);
        if (!matches) {
            throw new IllegalStateException(
                    "Idempotency-Key was reused with a different spending-limit reservation payload");
        }
    }

    private SpendingLimitDtos.ReserveResponse reservationResponse(
            final SpendingLimitReservation reservation, final boolean replay,
            final Account account, final AccountSpendingLimit limit, final String reason) {
        applyDue(limit);
        final BigDecimal dailyLimit = limitFor(limit, reservation.getOperationType());
        final BigDecimal used = reservations.used(reservation.getAccountId(), reservation.getOperationType(),
                reservation.getUsageDate());
        return new SpendingLimitDtos.ReserveResponse(true, replay, reservation.getCurrency(), dailyLimit,
                used, dailyLimit.subtract(used).max(BigDecimal.ZERO), reason,
                reservation.getReservationId(), reservation.getTransactionCorrelation(),
                reservation.getAmount(), reservation.getFingerprint(), reservation.getState().name(),
                reservation.getCreatedAt(), reservation.getUpdatedAt(), reservation.getExpiresAt(),
                reservation.getOutcome());
    }

    private AccountSpendingLimit lockedOrCreate(final Long accountId, final String user) {
        accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return limits.lock(accountId).orElseGet(() -> {
            final AccountSpendingLimit limit = new AccountSpendingLimit();
            limit.setAccountId(accountId);
            limit.setUpdatedAt(LocalDateTime.now());
            limit.setUpdatedBy(user);
            return limits.saveAndFlush(limit);
        });
    }

    private void applyDue(final AccountSpendingLimit limit) {
        if (limit.getPendingEffectiveAt() != null
                && !limit.getPendingEffectiveAt().isAfter(LocalDateTime.now())) {
            limit.setTransferDailyLimit(limit.getPendingTransferDailyLimit());
            limit.setWithdrawalDailyLimit(limit.getPendingWithdrawalDailyLimit());
            limit.setPendingTransferDailyLimit(null);
            limit.setPendingWithdrawalDailyLimit(null);
            limit.setPendingEffectiveAt(null);
            limits.save(limit);
        }
    }

    private BigDecimal limitFor(final AccountSpendingLimit limit, final String operationType) {
        return TRANSFER.equals(operationType)
                ? limit.getTransferDailyLimit() : limit.getWithdrawalDailyLimit();
    }

    private SpendingLimitDtos.LimitResponse view(final Account account, final AccountSpendingLimit limit) {
        final Long id = account.getId();
        final BigDecimal transferLimit = limit == null
                ? new BigDecimal("10000.00") : limit.getTransferDailyLimit();
        final BigDecimal withdrawalLimit = limit == null
                ? new BigDecimal("2000.00") : limit.getWithdrawalDailyLimit();
        return new SpendingLimitDtos.LimitResponse(id, account.getCurrency(), transferLimit, withdrawalLimit,
                reservations.used(id, TRANSFER, LocalDate.now()),
                reservations.used(id, WITHDRAWAL, LocalDate.now()),
                limit == null ? null : limit.getPendingTransferDailyLimit(),
                limit == null ? null : limit.getPendingWithdrawalDailyLimit(),
                limit == null ? null : limit.getPendingEffectiveAt());
    }

    private String normalizeOperation(final String operationType) {
        final String normalized = operationType == null ? "" : operationType.trim().toUpperCase(Locale.ROOT);
        if (!TRANSFER.equals(normalized) && !WITHDRAWAL.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported limit operation");
        }
        return normalized;
    }

    private String normalizeCurrency(final String currency) {
        final String normalized = currency == null || currency.isBlank()
                ? "USD" : currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter uppercase code");
        }
        return normalized;
    }

    private BigDecimal normalizeAmount(final BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Reservation amount must be positive");
        }
        return amount.stripTrailingZeros();
    }

    private String normalizeKey(final String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        final String normalized = idempotencyKey.trim();
        if (normalized.length() > 160 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Idempotency key must be 1-160 URL-safe characters");
        }
        return normalized;
    }

    private String fingerprint(final Long accountId, final String ownerId, final String operationType,
                               final BigDecimal amount, final String currency, final String idempotencyKey) {
        final String canonical = String.join("|", String.valueOf(accountId), ownerId.trim(), operationType,
                amount.stripTrailingZeros().toPlainString(), currency, idempotencyKey);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private long leaseMinutes() {
        return reservationLeaseMinutes > 0 ? reservationLeaseMinutes : 30;
    }

    private String firstNonBlank(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void audit(final Long accountId, final String userId, final String eventType,
                       final String operationType, final BigDecimal amount,
                       final BigDecimal dailyLimit, final BigDecimal dailyUsed, final String details) {
        audits.save(SpendingLimitAuditEvent.builder()
                .accountId(accountId)
                .userId(userId)
                .eventType(eventType)
                .operationType(operationType)
                .amount(amount)
                .dailyLimit(dailyLimit)
                .dailyUsed(dailyUsed)
                .details(details)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void notify(final String user, final Long account, final String title,
                        final String message, final String key) {
        notifications.createInternal(NotificationCreateRequest.builder()
                .userId(user)
                .type(NotificationType.SECURITY_ALERT)
                .severity(NotificationSeverity.WARNING)
                .title(title)
                .message(message)
                .sourceType(NotificationSourceType.ACCOUNT)
                .sourceId(String.valueOf(account))
                .dedupeKey(key)
                .build());
    }
}
