package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.entity.ScheduledTransfer;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferType;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccount;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerBalanceProjection;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import com.suhasan.finance.transaction_service.outcome.fx.OutcomeFxConverter;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import com.suhasan.finance.transaction_service.repository.ScheduledTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OutcomeAuthoritativeSourceService {
    public static final String FINGERPRINT_SCHEMA = "outcome-source-v2";

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBalanceProjectionRepository projectionRepository;
    private final ScheduledTransferRepository scheduledTransferRepository;
    private final OutcomeScheduledTransferForecaster scheduledTransferForecaster;
    private final OutcomeFxConverter fxConverter;
    private final ResilientAccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;

    public Snapshot capture(ScenarioRequest request, String userId, boolean rejectStaleObligation) {
        Instant capturedAt = Instant.now();
        List<String> accountIds = request.accountIds().stream().distinct().sorted().toList();
        Set<String> selectedAccounts = new LinkedHashSet<>(accountIds);
        List<LedgerAccountSnapshot> ledgerSnapshots = new ArrayList<>();
        List<AccountComponent> accountComponents = new ArrayList<>();

        for (String accountId : accountIds) {
            LedgerAccount ledger = ledgerAccountRepository.findByExternalAccountId(accountId)
                    .filter(candidate -> candidate.getAccountKind() == LedgerAccountKind.CUSTOMER)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Authoritative ledger account %s was not found".formatted(accountId)));
            if (!userId.equals(ledger.getOwnerId()) && rejectStaleObligation) {
                throw new AccessDeniedException("Selected ledger account is not owned by the authenticated customer");
            }
            LedgerBalanceProjection projection = projectionRepository.findById(ledger.getLedgerAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Authoritative balance projection was not found"));
            AccountDto account = accountServiceClient.getAccountInternal(accountId);
            if (account == null) {
                throw new IllegalStateException("Authoritative customer account state is unavailable");
            }
            Instant projectionTime = projection.getUpdatedAt() == null ? capturedAt
                    : projection.getUpdatedAt().toInstant(ZoneOffset.UTC);
            var conversion = fxConverter.convert(projection.getAvailableBalance(), ledger.getCurrency().trim(),
                    request.currency(), capturedAt);
            ledgerSnapshots.add(new LedgerAccountSnapshot(accountId, ledger.getCurrency().trim(),
                    money(projection.getAvailableBalance()), projection.getProjectionVersion(), projectionTime,
                    conversion.convertedAmount(), request.currency(), conversion.quote()));
            accountComponents.add(new AccountComponent(accountId, ledger.getLedgerAccountId().toString(),
                    ledger.getOwnerId(), ledger.getCurrency().trim(), ledger.getAccountKind().name(),
                    ledger.getStatus().name(), ledger.getVersion(), money(projection.getAvailableBalance()),
                    projection.getProjectionVersion(), account.getOwnerId(), normalized(account.getCurrency()),
                    normalized(account.getStatus()), account.getActive(), normalized(account.getAccountType())));
        }

        List<ScheduledTransfer> activeSchedules = scheduledTransferRepository
                .findByUserIdAndStatusOrderByNextRunAtAsc(userId, ScheduledTransferStatus.ACTIVE);
        List<ScheduledCashflowSnapshot> rawSchedules = scheduledTransferForecaster.forecast(
                request, userId, selectedAccounts, activeSchedules);
        List<ScheduledCashflowSnapshot> schedules = rawSchedules.stream().map(schedule -> {
            var conversion = fxConverter.convert(schedule.amount(), schedule.currency(), request.currency(), capturedAt);
            boolean sourceOwned = ledgerAccountRepository.findByExternalAccountId(schedule.fromAccountId())
                    .map(account -> userId.equals(account.getOwnerId())).orElse(false);
            boolean destinationOwned = ledgerAccountRepository.findByExternalAccountId(schedule.toAccountId())
                    .map(account -> userId.equals(account.getOwnerId())).orElse(false);
            boolean repairEligible = schedule.repairEligible() && sourceOwned;
            String ineligibleReason = repairEligible ? null : (sourceOwned
                    ? schedule.repairIneligibilityReason()
                    : "The schedule source is not an owned authoritative ledger account");
            return new ScheduledCashflowSnapshot(schedule.eventId(), schedule.scheduleId(), schedule.scheduledFor(),
                    schedule.date(), conversion.convertedAmount(), request.currency(), schedule.status(),
                    schedule.cadence(), schedule.evaluationTimeZone(), schedule.label(), schedule.fromAccountId(),
                    schedule.toAccountId(), schedule.sourceAmount(), schedule.sourceCurrency(), conversion.quote(),
                    schedule.scheduleVersion(), schedule.scheduleOwnerId(), schedule.sourceTimeZone(),
                    schedule.dueLocalDate(), sourceOwned, destinationOwned, repairEligible, ineligibleReason);
        }).toList();

        List<ScheduleComponent> scheduleComponents = activeSchedules.stream()
                .filter(schedule -> selectedAccounts.contains(schedule.getFromAccountId())
                        || selectedAccounts.contains(schedule.getToAccountId()))
                .map(this::scheduleComponent)
                .sorted(Comparator.comparing(ScheduleComponent::scheduleId))
                .toList();

        ProtectedObligationSnapshot protectedObligation = captureProtectedObligation(
                request, userId, selectedAccounts, schedules, capturedAt, rejectStaleObligation);
        ProtectedObligationComponent obligationComponent = protectedObligation == null ? null
                : new ProtectedObligationComponent(protectedObligation.scheduleId(),
                protectedObligation.scheduleVersion(), protectedObligation.status(), protectedObligation.ownerId(),
                protectedObligation.fromAccountId(), protectedObligation.toAccountId(),
                protectedObligation.sourceOwnedByCustomer(), protectedObligation.destinationOwnedByCustomer(),
                money(protectedObligation.amount()), protectedObligation.currency(),
                protectedObligation.scheduleType(), protectedObligation.cadence(), protectedObligation.dueAt(),
                protectedObligation.dueLocalDate(), protectedObligation.sourceTimeZone(),
                protectedObligation.evaluationTimeZone(), protectedObligation.endAt(),
                protectedObligation.sourceProjectionVersion(), protectedObligation.valid());

        SourceComponents components = new SourceComponents(FINGERPRINT_SCHEMA,
                List.copyOf(accountComponents), scheduleComponents, obligationComponent);
        return new Snapshot(List.copyOf(ledgerSnapshots), schedules, protectedObligation,
                fingerprint(components), components);
    }

    private ProtectedObligationSnapshot captureProtectedObligation(
            ScenarioRequest request, String userId, Set<String> selectedAccounts,
            List<ScheduledCashflowSnapshot> schedules, Instant capturedAt, boolean rejectStaleObligation) {
        if (request.effectiveOutcomeType() != OutcomeType.SCHEDULED_OBLIGATION) return null;

        Optional<ScheduledTransfer> found = scheduledTransferRepository.findById(request.protectedScheduleId());
        if (found.isEmpty()) {
            if (rejectStaleObligation) throw new AccessDeniedException("Protected scheduled obligation not found");
            return new ProtectedObligationSnapshot(request.protectedScheduleId(), -1L, "MISSING", null,
                    null, null, false, false, BigDecimal.ZERO.setScale(2), null,
                    "MISSING", "MISSING", null, null, null, request.timeZone(), null,
                    -1L, capturedAt, false, "The protected obligation is missing or changed.");
        }
        ScheduledTransfer schedule = found.get();
        if (!userId.equals(schedule.getUserId()) && rejectStaleObligation) {
            throw new AccessDeniedException("Protected scheduled obligation not found");
        }
        boolean sourceOwned = ledgerAccountRepository.findByExternalAccountId(schedule.getFromAccountId())
                .map(account -> userId.equals(account.getOwnerId())).orElse(false);
        boolean destinationOwned = ledgerAccountRepository.findByExternalAccountId(schedule.getToAccountId())
                .map(account -> userId.equals(account.getOwnerId())).orElse(false);
        LedgerAccount sourceLedger = ledgerAccountRepository.findByExternalAccountId(schedule.getFromAccountId())
                .filter(account -> account.getAccountKind() == LedgerAccountKind.CUSTOMER).orElse(null);
        long sourceProjectionVersion = sourceLedger == null ? -1L : projectionRepository
                .findById(sourceLedger.getLedgerAccountId())
                .map(LedgerBalanceProjection::getProjectionVersion).orElse(-1L);
        List<ScheduledCashflowSnapshot> occurrences = schedules.stream()
                .filter(value -> schedule.getScheduleId().equals(value.scheduleId())).toList();
        boolean valid = true;
        String invalidReason = null;
        if (!userId.equals(schedule.getUserId())) {
            valid = false;
            invalidReason = "The protected obligation owner changed.";
        } else if (!Objects.equals(schedule.getVersion(), request.protectedScheduleVersion())) {
            valid = false;
            invalidReason = "The protected obligation version changed after selection.";
        } else if (schedule.getStatus() != ScheduledTransferStatus.ACTIVE) {
            valid = false;
            invalidReason = "The protected obligation is no longer active.";
        } else if (!sourceOwned || !selectedAccounts.contains(schedule.getFromAccountId())) {
            valid = false;
            invalidReason = "The protected obligation must debit an owned selected ledger account.";
        } else if (occurrences.isEmpty()) {
            valid = false;
            invalidReason = "The protected obligation has no due occurrence inside the inclusive horizon.";
        }
        if (schedule.getCurrency() != null) {
            fxConverter.convert(schedule.getAmount(), schedule.getCurrency(), request.currency(), capturedAt);
        }
        if (rejectStaleObligation && !valid) throw new IllegalStateException(invalidReason);
        String sourceTimeZone = schedule.getSourceTimeZone() == null ? "UTC" : schedule.getSourceTimeZone();
        ZoneId sourceZone = ZoneId.of(sourceTimeZone);
        String cadence = schedule.getScheduleType() == ScheduledTransferType.ONE_TIME
                ? "ONE_TIME" : schedule.getFrequency().name();
        return new ProtectedObligationSnapshot(schedule.getScheduleId(),
                schedule.getVersion() == null ? 0L : schedule.getVersion(), schedule.getStatus().name(),
                schedule.getUserId(), schedule.getFromAccountId(), schedule.getToAccountId(), sourceOwned,
                destinationOwned, money(schedule.getAmount()), schedule.getCurrency(),
                schedule.getScheduleType().name(), cadence, schedule.getNextRunAt(),
                schedule.getNextRunAt().atZone(sourceZone).toLocalDate(), sourceZone.getId(),
                request.timeZone(), schedule.getEndAt(), sourceProjectionVersion, capturedAt, valid, invalidReason);
    }

    private ScheduleComponent scheduleComponent(ScheduledTransfer schedule) {
        return new ScheduleComponent(schedule.getScheduleId(), schedule.getUserId(), schedule.getFromAccountId(),
                schedule.getToAccountId(), money(schedule.getAmount()), schedule.getCurrency(),
                schedule.getScheduleType().name(), schedule.getFrequency() == null ? null : schedule.getFrequency().name(),
                schedule.getNextRunAt(), schedule.getEndAt(), schedule.getSourceTimeZone(),
                schedule.getSourceLocalDateTime(), schedule.getDstOverlapPolicy() == null ? null : schedule.getDstOverlapPolicy().name(),
                schedule.getDstGapPolicy() == null ? null : schedule.getDstGapPolicy().name(), schedule.getRecurrenceAnchorDay(),
                schedule.isRecurrenceAnchorEndOfMonth(), schedule.getStatus().name(), schedule.getReference(),
                schedule.getVersion() == null ? 0L : schedule.getVersion());
    }

    public String componentsJson(SourceComponents components) {
        try { return objectMapper.writeValueAsString(components); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to serialize source components", ex); }
    }

    private String fingerprint(Object value) {
        try {
            byte[] canonical = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to fingerprint authoritative source state", ex);
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_EVEN);
    }

    private String normalized(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public record Snapshot(List<LedgerAccountSnapshot> ledgerAccounts,
                           List<ScheduledCashflowSnapshot> scheduledCashflows,
                           ProtectedObligationSnapshot protectedObligation,
                           String sourceFingerprint,
                           SourceComponents components) {}

    public record SourceComponents(String schemaVersion, List<AccountComponent> accounts,
                                   List<ScheduleComponent> schedules,
                                   ProtectedObligationComponent protectedObligation) {}

    public record AccountComponent(String accountId, String ledgerAccountId, String ledgerOwnerId,
                                   String ledgerCurrency, String ledgerKind, String ledgerStatus,
                                   long ledgerVersion, BigDecimal availableBalance, long projectionVersion,
                                   String accountOwnerId, String accountCurrency, String accountStatus,
                                   Boolean accountActive, String accountType) {}

    public record ScheduleComponent(String scheduleId, String ownerId, String fromAccountId,
                                    String toAccountId, BigDecimal amount, String currency,
                                    String scheduleType, String frequency, Instant nextRunAt, Instant endAt,
                                    String sourceTimeZone, LocalDateTime sourceLocalDateTime,
                                    String dstOverlapPolicy, String dstGapPolicy, Integer recurrenceAnchorDay,
                                    boolean recurrenceAnchorEndOfMonth, String status, String reference,
                                    long version) {}

    public record ProtectedObligationComponent(String scheduleId, long scheduleVersion, String status,
                                               String ownerId, String fromAccountId, String toAccountId,
                                               boolean sourceOwnedByCustomer, boolean destinationOwnedByCustomer,
                                               BigDecimal amount, String currency, String scheduleType,
                                               String cadence, Instant dueAt, LocalDate dueLocalDate,
                                               String sourceTimeZone, String evaluationTimeZone, Instant endAt,
                                               long sourceProjectionVersion, boolean valid) {}
}
