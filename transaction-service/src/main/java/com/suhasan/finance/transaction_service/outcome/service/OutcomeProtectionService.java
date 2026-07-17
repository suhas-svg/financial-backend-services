package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.entity.*;
import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.*;
import com.suhasan.finance.transaction_service.outcome.domain.*;
import com.suhasan.finance.transaction_service.outcome.fx.FxRateQuote;
import com.suhasan.finance.transaction_service.outcome.fx.OutcomeFxConverter;
import com.suhasan.finance.transaction_service.outcome.repository.*;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import com.suhasan.finance.transaction_service.repository.ScheduledTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutcomeProtectionService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<AssumptionInput>> ASSUMPTION_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ShockInput>> SHOCK_LIST = new TypeReference<>() {};
    private static final TypeReference<List<LedgerAccountSnapshot>> LEDGER_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ScheduledCashflowSnapshot>> SCHEDULE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<RepairAction>> REPAIR_ACTION_LIST = new TypeReference<>() {};

    private final OutcomeScenarioRepository scenarioRepository;
    private final OutcomeScenarioVersionRepository versionRepository;
    private final OutcomeSimulationResultRepository resultRepository;
    private final OutcomeGuardrailDraftRepository guardrailRepository;
    private final OutcomeDomainEventRepository eventRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBalanceProjectionRepository projectionRepository;
    private final ScheduledTransferRepository scheduledTransferRepository;
    private final OutcomeSimulationEngine simulationEngine;
    private final OutcomeScheduledTransferForecaster scheduledTransferForecaster;
    private final OutcomeFxConverter fxConverter;
    private final OutcomeNotificationDeliveryService notificationDeliveryService;
    private final OutcomeGuardrailService guardrailService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ScenarioResponse create(ScenarioRequest request, String userId, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        validateRequest(request);
        String requestFingerprint = fingerprint(canonicalRequest(request));
        Optional<OutcomeScenario> replay = scenarioRepository.findByUserIdAndCreateIdempotencyKey(userId, key);
        if (replay.isPresent()) {
            requireSameFingerprint(replay.get().getCreateRequestFingerprint(), requestFingerprint);
            return response(replay.get());
        }

        Snapshot snapshot = captureSnapshot(request, userId, true);
        String scenarioId = UUID.randomUUID().toString();
        OutcomeScenario scenario = OutcomeScenario.builder()
                .scenarioId(scenarioId).userId(userId).name(request.name().trim()).status("ACTIVE")
                .currentVersion(1).currency(request.currency()).timeZone(request.timeZone())
                .createIdempotencyKey(key).createRequestFingerprint(requestFingerprint)
                .lastSourceFingerprint(snapshot.sourceFingerprint()).build();
        SimulationProof simulation = simulate(request, snapshot);
        scenario.setLastProtectionState(simulation.baseline().safe() ? "SAFE" : "AT_RISK");
        scenario.setLastCheckedAt(Instant.now());
        scenarioRepository.save(scenario);
        return persistVersionAndResult(scenario, 1, request, snapshot, simulation, key, requestFingerprint);
    }

    @Transactional
    public ScenarioResponse createVersion(String scenarioId, ScenarioRequest request, String userId, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        validateRequest(request);
        OutcomeScenario scenario = ownedScenario(scenarioId, userId);
        if (!scenario.getCurrency().equals(request.currency())) {
            throw new IllegalArgumentException("A scenario version cannot change currency");
        }
        String requestFingerprint = fingerprint(canonicalRequest(request));
        Optional<OutcomeScenarioVersion> replay = versionRepository.findByScenarioIdAndMutationIdempotencyKey(scenarioId, key);
        if (replay.isPresent()) {
            requireSameFingerprint(replay.get().getRequestFingerprint(), requestFingerprint);
            return response(scenario, replay.get().getScenarioVersion());
        }

        Snapshot snapshot = captureSnapshot(request, userId, true);
        SimulationProof simulation = simulate(request, snapshot);
        int nextVersion = scenario.getCurrentVersion() + 1;
        scenario.setCurrentVersion(nextVersion);
        scenario.setName(request.name().trim());
        scenario.setTimeZone(request.timeZone());
        scenario.setLastSourceFingerprint(snapshot.sourceFingerprint());
        scenario.setLastProtectionState(simulation.baseline().safe() ? "SAFE" : "AT_RISK");
        scenario.setLastCheckedAt(Instant.now());
        scenarioRepository.save(scenario);
        return persistVersionAndResult(scenario, nextVersion, request, snapshot, simulation, key, requestFingerprint);
    }

    @Transactional(readOnly = true)
    public List<ScenarioSummary> list(String userId) {
        return scenarioRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(scenario -> {
            OutcomeScenarioVersion version = requiredVersion(scenario, scenario.getCurrentVersion());
            OutcomeSimulationResult result = requiredResult(scenario.getScenarioId(), scenario.getCurrentVersion());
            ProtectedObligationSnapshot obligation = version.getProtectedObligationJson() == null
                    ? null : read(version.getProtectedObligationJson(), ProtectedObligationSnapshot.class);
            return new ScenarioSummary(scenario.getScenarioId(), scenario.getName(), scenario.getCurrentVersion(),
                    scenario.getStatus(), scenario.getCurrency(), version.getHorizonStart(), version.getHorizonDays(),
                    version.getProtectedMinimum(), result.isBaselineSafe(), scenario.getUpdatedAt(),
                    version.getOutcomeType() == null ? OutcomeType.BALANCE_FLOOR : OutcomeType.valueOf(version.getOutcomeType()),
                    obligation == null ? null : obligation.scheduleId());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ScenarioResponse get(String scenarioId, String userId) {
        return response(ownedScenario(scenarioId, userId));
    }

    @Transactional
    public GuardrailResponse acceptGuardrail(String guardrailId, GuardrailAcceptRequest request,
                                             String userId, String idempotencyKey) {
        if (request == null || !request.confirmed()) {
            throw new IllegalArgumentException("Explicit confirmation is required to accept a guardrail draft");
        }
        String key = requireIdempotencyKey(idempotencyKey);
        OutcomeGuardrailDraft draft = guardrailRepository.findByGuardrailIdAndUserId(guardrailId, userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail draft not found"));
        String acceptanceFingerprint = fingerprint(Map.of("guardrailId", guardrailId, "confirmed", true));
        guardrailRepository.findByUserIdAndAcceptanceIdempotencyKey(userId, key)
                .filter(existing -> !existing.getGuardrailId().equals(guardrailId))
                .ifPresent(existing -> { throw new IllegalStateException(
                        "Idempotency-Key was already used to accept a different guardrail draft"); });
        if ("ACCEPTED".equals(draft.getStatus())) {
            requireSameFingerprint(draft.getAcceptanceFingerprint(), acceptanceFingerprint);
            if (!key.equals(draft.getAcceptanceIdempotencyKey())) {
                throw new IllegalStateException("Guardrail draft was already accepted with a different idempotency key");
            }
            return guardrailResponse(draft);
        }
        if (draft.getExpiresAt().isBefore(Instant.now())) {
            draft.setStatus("EXPIRED");
            guardrailRepository.save(draft);
            throw new IllegalStateException("Guardrail draft has expired");
        }
        draft.setStatus("ACCEPTED");
        draft.setAcceptedAt(Instant.now());
        draft.setAcceptanceIdempotencyKey(key);
        draft.setAcceptanceFingerprint(acceptanceFingerprint);
        guardrailRepository.save(draft);
        OutcomeScenario scenario = scenarioRepository.findById(draft.getScenarioId()).orElseThrow();
        recordEvent("GUARDRAIL_DRAFT_ACCEPTED", scenario, draft.getResultId(), draft.getGuardrailId(),
                "guardrail-accepted:" + draft.getGuardrailId(), Map.of(
                        "guardrailType", draft.getGuardrailType(), "threshold", draft.getThresholdAmount(),
                        "currency", draft.getCurrency(), "previewOnly", true));
        return guardrailResponse(draft);
    }

    @Transactional
    public GuardrailResponse selectRepairDraft(String guardrailId, RepairDraftSelectRequest request,
                                               String userId, String idempotencyKey) {
        if (request == null || !request.confirmed()) {
            throw new IllegalArgumentException("Explicit confirmation is required to select a repair draft");
        }
        String key = requireIdempotencyKey(idempotencyKey);
        OutcomeGuardrailDraft draft = guardrailRepository.findByGuardrailIdAndUserId(guardrailId, userId)
                .orElseThrow(() -> new AccessDeniedException("Repair draft not found"));
        if (draft.getAlternativeRank() == null) {
            throw new IllegalArgumentException("Only ranked repair alternatives can be selected");
        }
        String selectionFingerprint = fingerprint(Map.of("guardrailId", guardrailId, "confirmed", true));
        guardrailRepository.findByUserIdAndPreviewSelectionIdempotencyKey(userId, key)
                .filter(existing -> !existing.getGuardrailId().equals(guardrailId))
                .ifPresent(existing -> { throw new IllegalStateException(
                        "Idempotency-Key was already used to select a different repair draft"); });
        if (draft.getPreviewSelectedAt() != null) {
            requireSameFingerprint(draft.getPreviewSelectionFingerprint(), selectionFingerprint);
            if (!key.equals(draft.getPreviewSelectionIdempotencyKey())) {
                throw new IllegalStateException("Repair draft was already selected with a different idempotency key");
            }
            return guardrailResponse(draft);
        }
        draft.setPreviewSelectedAt(Instant.now());
        draft.setPreviewSelectionIdempotencyKey(key);
        draft.setPreviewSelectionFingerprint(selectionFingerprint);
        guardrailRepository.save(draft);
        OutcomeScenario scenario = scenarioRepository.findById(draft.getScenarioId()).orElseThrow();
        recordEvent("REPAIR_DRAFT_SELECTED", scenario, draft.getResultId(), draft.getGuardrailId(),
                "repair-selected:" + draft.getGuardrailId(), Map.of(
                        "alternativeRank", draft.getAlternativeRank(),
                        "certificateHash", draft.getReplayCertificateHash(),
                        "previewOnly", true, "financialMutation", false));
        return guardrailResponse(draft);
    }

    @Transactional
    public WarningAcknowledgementResponse acknowledgeWarning(String eventId, String userId, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        OutcomeDomainEvent warning = eventRepository.findByEventIdAndUserId(eventId, userId)
                .filter(event -> "OUTCOME_PROTECTION_AT_RISK".equals(event.getEventType()))
                .orElseThrow(() -> new AccessDeniedException("Warning not found"));
        OutcomeScenario scenario = ownedScenario(warning.getScenarioId(), userId);
        OutcomeDomainEvent acknowledgement = recordEvent("WARNING_ACKNOWLEDGED", scenario, warning.getResultId(), null,
                "warning-ack:" + warning.getEventId(),
                Map.of("warningEventId", warning.getEventId(), "idempotencyKey", key));
        return new WarningAcknowledgementResponse(acknowledgement.getEventId(), warning.getEventId(), true, acknowledgement.getCreatedAt());
    }

    @Transactional
    public DivergenceResponse refresh(String scenarioId, String userId) {
        return refreshScenario(ownedScenario(scenarioId, userId));
    }

    @Scheduled(fixedDelayString = "${outcome-protection.monitor.fixed-delay-ms:300000}",
            initialDelayString = "${outcome-protection.monitor.initial-delay-ms:60000}")
    @Transactional
    public void monitorActiveScenarios() {
        for (OutcomeScenario scenario : scenarioRepository.findTop100ByStatusOrderByLastCheckedAtAsc("ACTIVE")) {
            try {
                refreshScenario(scenario);
            } catch (RuntimeException ex) {
                log.warn("Outcome Protection monitor failed for scenario {}: {}", scenario.getScenarioId(), ex.getMessage());
            }
        }
    }

    private DivergenceResponse refreshScenario(OutcomeScenario scenario) {
        OutcomeScenarioVersion saved = requiredVersion(scenario, scenario.getCurrentVersion());
        OutcomeSimulationResult savedResult = requiredResult(scenario.getScenarioId(), scenario.getCurrentVersion());
        ScenarioRequest request = requestFrom(scenario, saved);
        Snapshot fresh = captureSnapshot(request, scenario.getUserId(), false);
        SimulationProof simulation = simulate(request, fresh);
        String previousFingerprint = scenario.getLastSourceFingerprint();
        boolean diverged = !fresh.sourceFingerprint().equals(saved.getSourceFingerprint());
        boolean atRisk = !simulation.baseline().safe();
        boolean notificationEmitted = false;
        OutcomeDomainEvent warning = null;
        NotificationDeliveryEvidence notificationDelivery = null;

        Map<String, Object> evaluationFields = new LinkedHashMap<>();
        evaluationFields.put("savedSourceFingerprint", saved.getSourceFingerprint());
        evaluationFields.put("previousObservedSourceFingerprint", previousFingerprint);
        evaluationFields.put("currentSourceFingerprint", fresh.sourceFingerprint());
        evaluationFields.put("savedBaselineSafe", savedResult.isBaselineSafe());
        evaluationFields.put("freshBaselineSafe", simulation.baseline().safe());
        evaluationFields.put("protectedMinimum", simulation.baseline().protectedMinimum());
        evaluationFields.put("lowestBalance", simulation.baseline().lowestBalance());
        evaluationFields.put("failureDate", simulation.baseline().failureDate());
        evaluationFields.put("ledgerSnapshot", fresh.ledgerAccounts());
        evaluationFields.put("scheduleSnapshot", fresh.scheduledCashflows());
        OutcomeDomainEvent evaluation = recordEvent("DIVERGENCE_EVALUATED", scenario, savedResult.getResultId(), null,
                "outcome-evaluation:" + scenario.getScenarioId() + ":" + scenario.getCurrentVersion() + ":" + fresh.sourceFingerprint(),
                evaluationFields);

        if (diverged && savedResult.isBaselineSafe() && atRisk) {
            String warningDedupe = "outcome-risk:" + scenario.getScenarioId() + ":" + scenario.getCurrentVersion() + ":" + fresh.sourceFingerprint();
            warning = recordEvent("OUTCOME_PROTECTION_AT_RISK", scenario, savedResult.getResultId(), null,
                    warningDedupe, Map.of("failureDate", String.valueOf(simulation.baseline().failureDate()),
                            "lowestBalance", simulation.baseline().lowestBalance(),
                            "protectedMinimum", simulation.baseline().protectedMinimum(),
                            "sourceFingerprint", fresh.sourceFingerprint(),
                            "evaluationEventId", evaluation.getEventId()));
            OutcomeNotificationDelivery delivery = notificationDeliveryService.enqueue(warning, scenario, simulation);
            notificationDelivery = notificationDeliveryService.evidence(delivery);
            notificationEmitted = "DELIVERED".equals(delivery.getState());
        }
        boolean warningAcknowledged = warning != null && eventRepository
                .findByUserIdAndDedupeKey(scenario.getUserId(), "warning-ack:" + warning.getEventId()).isPresent();
        scenario.setLastSourceFingerprint(fresh.sourceFingerprint());
        scenario.setLastProtectionState(atRisk ? "AT_RISK" : "SAFE");
        scenario.setLastCheckedAt(Instant.now());
        scenarioRepository.save(scenario);
        return new DivergenceResponse(scenario.getScenarioId(), previousFingerprint, fresh.sourceFingerprint(),
                evaluation.getEventId(), warning == null ? null : warning.getEventId(),
                diverged, atRisk, warningAcknowledged, notificationEmitted, notificationDelivery,
                simulation, scenario.getLastCheckedAt());
    }

    private ScenarioResponse persistVersionAndResult(OutcomeScenario scenario, int number, ScenarioRequest request,
                                                     Snapshot snapshot, SimulationProof simulation,
                                                     String mutationKey, String requestFingerprint) {
        OutcomeScenarioVersion version = OutcomeScenarioVersion.builder()
                .versionId(UUID.randomUUID().toString()).scenarioId(scenario.getScenarioId()).scenarioVersion(number)
                .horizonStart(request.horizonStart()).horizonDays(request.horizonDays())
                .protectedMinimum(money(request.protectedMinimum()))
                .outcomeType(request.effectiveOutcomeType().name())
                .protectedObligationJson(snapshot.protectedObligation() == null ? null : json(snapshot.protectedObligation()))
                .canonicalInputsJson(json(canonicalRequest(request)))
                .accountIdsJson(json(sortedDistinct(request.accountIds())))
                .assumptionsJson(json(sortedAssumptions(request.assumptions()))).shocksJson(json(sortedShocks(request.shocks())))
                .ledgerSnapshotJson(json(snapshot.ledgerAccounts())).scheduleSnapshotJson(json(snapshot.scheduledCashflows()))
                .sourceFingerprint(snapshot.sourceFingerprint()).mutationIdempotencyKey(mutationKey)
                .requestFingerprint(requestFingerprint).build();
        versionRepository.save(version);

        String resultId = UUID.randomUUID().toString();
        OutcomeSimulationResult result = OutcomeSimulationResult.builder()
                .resultId(resultId).scenarioId(scenario.getScenarioId()).scenarioVersion(number)
                .baselineSafe(simulation.baseline().safe()).baselineLowestBalance(simulation.baseline().lowestBalance())
                .baselineFailureDate(simulation.baseline().failureDate()).proofJson(json(simulation))
                .failureJson(json(simulation.reverseStress())).repairJson(json(simulation.repair()))
                .evaluatedCombinations(simulation.evaluatedCombinations()).searchCapped(simulation.searchCapped())
                .resultFingerprint(fingerprint(simulation))
                .engineVersion(simulation.repair().engineVersion())
                .canonicalInputsJson(json(canonicalRequest(request)))
                .sourceVersionsJson(json(Map.of(
                        "ledger", snapshot.ledgerAccounts().stream().collect(java.util.stream.Collectors.toMap(
                                LedgerAccountSnapshot::accountId, LedgerAccountSnapshot::projectionVersion)),
                        "schedules", snapshot.scheduledCashflows().stream().collect(java.util.stream.Collectors.toMap(
                                ScheduledCashflowSnapshot::eventId, value -> value.scheduleVersion() == null ? 0L : value.scheduleVersion())))))
                .candidateActionsJson(json(simulation.repair().alternatives().stream()
                        .flatMap(value -> value.actions().stream()).distinct().toList()))
                .replayOutputJson(json(simulation.repair().alternatives()))
                .certificateHash(simulation.repair().certificateHash())
                .rankingFactorsJson(json(simulation.repair().alternatives().stream()
                        .map(RepairAlternative::rankingFactors).toList()))
                .rejectionReasonsJson(json(simulation.repair().rejectedCandidates()))
                .repairEvaluatedCombinations(simulation.repair().evaluatedCombinations())
                .repairSearchCapped(simulation.repair().searchCapped())
                .build();
        resultRepository.save(result);
        createGuardrails(scenario, version, result, simulation, request.accountIds());
        recordEvent("SCENARIO_COMPLETED", scenario, resultId, null,
                "scenario-completed:" + scenario.getScenarioId() + ":" + number,
                Map.of("baselineSafe", simulation.baseline().safe(), "evaluatedCombinations", simulation.evaluatedCombinations(),
                        "searchCapped", simulation.searchCapped(), "sourceFingerprint", snapshot.sourceFingerprint()));
        if (simulation.baseline().safe() && !eventRepository.existsByUserIdAndEventType(scenario.getUserId(), "FIRST_PROTECTED_OUTCOME")) {
            recordEvent("FIRST_PROTECTED_OUTCOME", scenario, resultId, null,
                    "first-protected-outcome:" + scenario.getUserId(),
                    Map.of("protectedMinimum", version.getProtectedMinimum(), "currency", scenario.getCurrency()));
        }
        return response(scenario, number);
    }

    private void createGuardrails(OutcomeScenario scenario, OutcomeScenarioVersion version,
                                  OutcomeSimulationResult result, SimulationProof simulation, List<String> accountIds) {
        ZoneId zone = ZoneId.of(scenario.getTimeZone());
        Instant expiresAt = version.getHorizonStart().plusDays(version.getHorizonDays())
                .atStartOfDay(zone).toInstant();
        List<String> scope = sortedDistinct(accountIds);
        guardrailRepository.save(OutcomeGuardrailDraft.builder()
                .guardrailId(UUID.randomUUID().toString()).scenarioId(scenario.getScenarioId())
                .resultId(result.getResultId()).userId(scenario.getUserId()).guardrailType("LOW_BALANCE_WARNING")
                .thresholdAmount(version.getProtectedMinimum()).currency(scenario.getCurrency()).scopeJson(json(scope))
                .previewText("Warn me when projected available balance drops below %s %s before this scenario expires. No money will move."
                        .formatted(scenario.getCurrency(), version.getProtectedMinimum().toPlainString()))
                .expiresAt(expiresAt).build());
        for (RepairAlternative alternative : simulation.repair().alternatives()) {
            List<RepairAction> actions = alternative.actions();
            String type = actions.size() == 1 ? actions.getFirst().type() : "REPAIR_BUNDLE";
            BigDecimal threshold = actions.stream().map(RepairAction::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            guardrailRepository.save(OutcomeGuardrailDraft.builder()
                    .guardrailId(UUID.randomUUID().toString()).scenarioId(scenario.getScenarioId())
                    .resultId(result.getResultId()).userId(scenario.getUserId()).guardrailType(type)
                    .thresholdAmount(threshold).currency(scenario.getCurrency()).scopeJson(json(scope))
                    .alternativeRank(alternative.rank()).candidateActionsJson(json(actions))
                    .replayProofJson(json(alternative.replay()))
                    .replayCertificateHash(alternative.certificateHash())
                    .rankingFactorsJson(json(alternative.rankingFactors()))
                    .rejectionReasonsJson(json(simulation.repair().rejectedCandidates()))
                    .previewText(alternative.explanation() + " This is a replay-proven preview; selection cannot mutate schedules, limits, ledgers, or funds.")
                    .expiresAt(expiresAt).build());
        }
    }

    private Snapshot captureSnapshot(ScenarioRequest request, String userId, boolean rejectStaleObligation) {
        Instant capturedAt = Instant.now();
        List<String> accountIds = sortedDistinct(request.accountIds());
        Set<String> selectedAccounts = new LinkedHashSet<>(accountIds);
        List<LedgerAccountSnapshot> ledger = new ArrayList<>();
        for (String accountId : accountIds) {
            LedgerAccount account = ledgerAccountRepository.findByExternalAccountId(accountId)
                    .filter(candidate -> candidate.getAccountKind() == LedgerAccountKind.CUSTOMER)
                    .orElseThrow(() -> new IllegalArgumentException("Authoritative ledger account %s was not found".formatted(accountId)));
            if (!userId.equals(account.getOwnerId())) {
                throw new AccessDeniedException("Selected ledger account is not owned by the authenticated customer");
            }
            LedgerBalanceProjection projection = projectionRepository.findById(account.getLedgerAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Authoritative balance projection was not found"));
            Instant projectionTime = projection.getUpdatedAt() == null ? capturedAt
                    : projection.getUpdatedAt().toInstant(ZoneOffset.UTC);
            var conversion = fxConverter.convert(projection.getAvailableBalance(), account.getCurrency().trim(),
                    request.currency(), capturedAt);
            ledger.add(new LedgerAccountSnapshot(accountId, account.getCurrency().trim(),
                    money(projection.getAvailableBalance()), projection.getProjectionVersion(), projectionTime,
                    conversion.convertedAmount(), request.currency(), conversion.quote()));
        }

        List<ScheduledCashflowSnapshot> rawSchedules = scheduledTransferForecaster.forecast(request, userId,
                selectedAccounts,
                scheduledTransferRepository.findByUserIdAndStatusOrderByNextRunAtAsc(userId, ScheduledTransferStatus.ACTIVE));
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
                    schedule.toAccountId(), conversion.sourceAmount(), conversion.sourceCurrency(), conversion.quote(),
                    schedule.scheduleVersion(), schedule.scheduleOwnerId(), schedule.sourceTimeZone(),
                    schedule.dueLocalDate(), sourceOwned, destinationOwned, repairEligible, ineligibleReason);
        }).toList();

        ProtectedObligationSnapshot protectedObligation = null;
        if (request.effectiveOutcomeType() == OutcomeType.SCHEDULED_OBLIGATION) {
            ScheduledTransfer schedule = scheduledTransferRepository.findById(request.protectedScheduleId())
                    .orElseThrow(() -> new AccessDeniedException("Protected scheduled obligation not found"));
            if (!userId.equals(schedule.getUserId())) {
                throw new AccessDeniedException("Protected scheduled obligation not found");
            }
            boolean sourceOwned = ledgerAccountRepository.findByExternalAccountId(schedule.getFromAccountId())
                    .map(account -> userId.equals(account.getOwnerId())).orElse(false);
            boolean destinationOwned = ledgerAccountRepository.findByExternalAccountId(schedule.getToAccountId())
                    .map(account -> userId.equals(account.getOwnerId())).orElse(false);
            LedgerAccount sourceLedger = ledgerAccountRepository.findByExternalAccountId(schedule.getFromAccountId())
                    .filter(account -> account.getAccountKind() == LedgerAccountKind.CUSTOMER)
                    .orElse(null);
            long sourceProjectionVersion = sourceLedger == null ? -1L : projectionRepository
                    .findById(sourceLedger.getLedgerAccountId())
                    .map(LedgerBalanceProjection::getProjectionVersion).orElse(-1L);
            List<ScheduledCashflowSnapshot> occurrences = schedules.stream()
                    .filter(value -> schedule.getScheduleId().equals(value.scheduleId())).toList();
            boolean valid = true;
            String invalidReason = null;
            if (!Objects.equals(schedule.getVersion(), request.protectedScheduleVersion())) {
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
            fxConverter.convert(schedule.getAmount(), schedule.getCurrency(), request.currency(), capturedAt);
            if (rejectStaleObligation && !valid) {
                throw new IllegalStateException(invalidReason);
            }
            ZoneId sourceZone = ZoneId.of(schedule.getSourceTimeZone() == null
                    ? "UTC" : schedule.getSourceTimeZone());
            String cadence = schedule.getScheduleType() == ScheduledTransferType.ONE_TIME
                    ? "ONE_TIME" : schedule.getFrequency().name();
            protectedObligation = new ProtectedObligationSnapshot(
                    schedule.getScheduleId(), schedule.getVersion() == null ? 0L : schedule.getVersion(),
                    schedule.getStatus().name(), schedule.getUserId(), schedule.getFromAccountId(),
                    schedule.getToAccountId(), sourceOwned, destinationOwned, money(schedule.getAmount()),
                    schedule.getCurrency(), schedule.getScheduleType().name(), cadence, schedule.getNextRunAt(),
                    schedule.getNextRunAt().atZone(sourceZone).toLocalDate(), sourceZone.getId(),
                    request.timeZone(), schedule.getEndAt(), sourceProjectionVersion, capturedAt, valid, invalidReason);
        }
        String sourceFingerprint = fingerprint(new SourceFingerprint(ledger, schedules, protectedObligation));
        return new Snapshot(ledger, schedules, protectedObligation, sourceFingerprint);
    }
    private SimulationProof simulate(ScenarioRequest request, Snapshot snapshot) {
        List<OutcomeSimulationEngine.Cashflow> cashflows = new ArrayList<>();
        for (AssumptionInput assumption : sortedAssumptions(request.assumptions())) {
            cashflows.add(new OutcomeSimulationEngine.Cashflow(assumption.id(), assumption.date(), money(assumption.amount()),
                    "ASSUMPTION", assumption.label(), assumption.flexible(), assumption.critical()));
        }
        for (ScheduledCashflowSnapshot schedule : snapshot.scheduledCashflows()) {
            boolean protectedObligation = snapshot.protectedObligation() != null
                    && snapshot.protectedObligation().scheduleId().equals(schedule.scheduleId());
            cashflows.add(new OutcomeSimulationEngine.Cashflow(schedule.eventId(), schedule.date(), schedule.amount(),
                    "SCHEDULED_TRANSFER", schedule.label(), schedule.repairEligible(), protectedObligation,
                    schedule.scheduleId(), protectedObligation,
                    schedule.repairEligible() && !protectedObligation, schedule.repairIneligibilityReason()));
        }
        BigDecimal starting = snapshot.ledgerAccounts().stream()
                .map(account -> account.baseAvailableBalance() == null
                        ? account.availableBalance() : account.baseAvailableBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        OutcomeSimulationEngine.ProtectionTarget target = request.effectiveOutcomeType() == OutcomeType.SCHEDULED_OBLIGATION
                ? new OutcomeSimulationEngine.ProtectionTarget(OutcomeType.SCHEDULED_OBLIGATION,
                    request.protectedScheduleId(), snapshot.protectedObligation() != null
                            && snapshot.protectedObligation().valid(),
                    snapshot.protectedObligation() == null
                            ? "The protected obligation snapshot is missing."
                            : snapshot.protectedObligation().invalidReason())
                : OutcomeSimulationEngine.ProtectionTarget.balanceFloor();
        return simulationEngine.simulate(money(starting), money(request.protectedMinimum()),
                request.horizonStart(), request.horizonDays(), cashflows, sortedShocks(request.shocks()), target);
    }
    private ScenarioResponse response(OutcomeScenario scenario) { return response(scenario, scenario.getCurrentVersion()); }

    private ScenarioResponse response(OutcomeScenario scenario, int number) {
        OutcomeScenarioVersion version = requiredVersion(scenario, number);
        OutcomeSimulationResult result = requiredResult(scenario.getScenarioId(), number);
        List<String> accountIds = read(version.getAccountIdsJson(), STRING_LIST);
        List<LedgerAccountSnapshot> ledger = read(version.getLedgerSnapshotJson(), LEDGER_LIST);
        List<ScheduledCashflowSnapshot> schedules = read(version.getScheduleSnapshotJson(), SCHEDULE_LIST);
        BigDecimal starting = ledger.stream()
                .map(account -> account.baseAvailableBalance() == null ? account.availableBalance() : account.baseAvailableBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<FxRateQuote> fxQuotes = new ArrayList<>();
        ledger.stream().map(LedgerAccountSnapshot::fxQuote).filter(Objects::nonNull).forEach(fxQuotes::add);
        schedules.stream().map(ScheduledCashflowSnapshot::fxQuote).filter(Objects::nonNull).forEach(fxQuotes::add);
        ProtectedObligationSnapshot obligation = version.getProtectedObligationJson() == null
                ? null : read(version.getProtectedObligationJson(), ProtectedObligationSnapshot.class);
        SourceSnapshot source = new SourceSnapshot(money(starting), scenario.getCurrency(), ledger, schedules,
                obligation, fxQuotes.stream().distinct().toList(), false, version.getSourceFingerprint());
        List<GuardrailResponse> guardrails = guardrailRepository.findByResultIdOrderByCreatedAtAsc(result.getResultId())
                .stream().map(this::guardrailResponse).toList();
        return new ScenarioResponse(scenario.getScenarioId(), scenario.getName(), number, scenario.getStatus(),
                scenario.getCurrency(), scenario.getTimeZone(), version.getHorizonStart(), version.getHorizonDays(),
                version.getProtectedMinimum(), accountIds, read(version.getAssumptionsJson(), ASSUMPTION_LIST),
                read(version.getShocksJson(), SHOCK_LIST), source, read(result.getProofJson(), SimulationProof.class),
                guardrails, version.getCreatedAt(), version.getOutcomeType() == null ? OutcomeType.BALANCE_FLOOR : OutcomeType.valueOf(version.getOutcomeType()),
                obligation == null ? null : obligation.scheduleId(),
                obligation == null ? null : obligation.scheduleVersion());
    }

    private GuardrailResponse guardrailResponse(OutcomeGuardrailDraft draft) {
        return new GuardrailResponse(draft.getGuardrailId(), draft.getGuardrailType(), draft.getThresholdAmount(),
                draft.getCurrency(), read(draft.getScopeJson(), STRING_LIST), draft.getExpiresAt(), draft.getStatus(),
                draft.getPreviewText(), draft.getAcceptedAt(),
                guardrailService.optionalPolicy(draft.getGuardrailId(), draft.getUserId()),
                draft.getAlternativeRank(), read(draft.getCandidateActionsJson(), REPAIR_ACTION_LIST),
                draft.getReplayCertificateHash(), draft.getRankingFactorsJson() == null
                    ? null : read(draft.getRankingFactorsJson(), RepairRankingFactors.class),
                draft.getPreviewSelectedAt());
    }

    private ScenarioRequest requestFrom(OutcomeScenario scenario, OutcomeScenarioVersion version) {
        ProtectedObligationSnapshot obligation = version.getProtectedObligationJson() == null
                ? null : read(version.getProtectedObligationJson(), ProtectedObligationSnapshot.class);
        return new ScenarioRequest(scenario.getName(), read(version.getAccountIdsJson(), STRING_LIST),
                scenario.getCurrency(), scenario.getTimeZone(), version.getHorizonStart(), version.getHorizonDays(),
                version.getProtectedMinimum(), read(version.getAssumptionsJson(), ASSUMPTION_LIST),
                read(version.getShocksJson(), SHOCK_LIST), version.getOutcomeType() == null ? OutcomeType.BALANCE_FLOOR : OutcomeType.valueOf(version.getOutcomeType()),
                obligation == null ? null : obligation.scheduleId(),
                obligation == null ? null : obligation.scheduleVersion());
    }

    private OutcomeDomainEvent recordEvent(String type, OutcomeScenario scenario, String resultId, String guardrailId,
                                           String dedupeKey, Map<String, ?> fields) {
        Optional<OutcomeDomainEvent> existing = eventRepository.findByUserIdAndDedupeKey(scenario.getUserId(), dedupeKey);
        if (existing.isPresent()) return existing.get();
        return eventRepository.save(OutcomeDomainEvent.builder()
                .eventId(UUID.randomUUID().toString()).eventType(type).userId(scenario.getUserId())
                .scenarioId(scenario.getScenarioId()).scenarioVersion(scenario.getCurrentVersion())
                .resultId(resultId).guardrailId(guardrailId).dedupeKey(dedupeKey).fieldsJson(json(fields)).build());
    }

    private void validateRequest(ScenarioRequest request) {
        ZoneId.of(request.timeZone());
        if (request.effectiveOutcomeType() == OutcomeType.SCHEDULED_OBLIGATION) {
            if (request.accountIds().size() != 1) {
                throw new IllegalArgumentException(
                        "Scheduled-obligation outcomes must select exactly one authoritative source account");
            }
            if (request.protectedScheduleId() == null || request.protectedScheduleId().isBlank()) {
                throw new IllegalArgumentException("A protected schedule is required for a scheduled-obligation outcome");
            }
            if (request.protectedScheduleVersion() == null || request.protectedScheduleVersion() < 0) {
                throw new IllegalArgumentException("The current protected schedule version is required");
            }
        } else if (request.protectedScheduleId() != null || request.protectedScheduleVersion() != null) {
            throw new IllegalArgumentException("Balance-floor outcomes must not include a protected schedule");
        }
        if (request.accountIds().stream().distinct().count() != request.accountIds().size())
            throw new IllegalArgumentException("Selected account IDs must be unique");
        LocalDate end = request.horizonStart().plusDays(request.horizonDays() - 1L);
        Set<String> assumptionIds = new HashSet<>();
        for (AssumptionInput assumption : request.assumptions()) {
            if (!assumptionIds.add(assumption.id())) throw new IllegalArgumentException("Assumption IDs must be unique");
            if (assumption.date().isBefore(request.horizonStart()) || assumption.date().isAfter(end))
                throw new IllegalArgumentException("Assumption dates must be inside the scenario horizon");
            if (assumption.type() == AssumptionType.INCOME && assumption.amount().signum() <= 0)
                throw new IllegalArgumentException("Income assumptions must be positive");
            if (assumption.type() == AssumptionType.EXPENSE && assumption.amount().signum() >= 0)
                throw new IllegalArgumentException("Expense assumptions must be negative");
        }
        Set<String> shockIds = new HashSet<>();
        for (ShockInput shock : request.shocks()) {
            if (!shockIds.add(shock.id())) throw new IllegalArgumentException("Shock IDs must be unique");
            if (!assumptionIds.contains(shock.targetAssumptionId()))
                throw new IllegalArgumentException("Every shock must target a submitted assumption");
        }
    }

    private OutcomeScenario ownedScenario(String scenarioId, String userId) {
        return scenarioRepository.findByScenarioIdAndUserId(scenarioId, userId)
                .orElseThrow(() -> new AccessDeniedException("Outcome Protection scenario not found"));
    }

    private OutcomeScenarioVersion requiredVersion(OutcomeScenario scenario, int number) {
        return versionRepository.findByScenarioIdAndScenarioVersion(scenario.getScenarioId(), number).orElseThrow();
    }

    private OutcomeSimulationResult requiredResult(String scenarioId, int number) {
        return resultRepository.findByScenarioIdAndScenarioVersion(scenarioId, number).orElseThrow();
    }

    private List<String> sortedDistinct(List<String> values) {
        return values.stream().map(String::trim).distinct().sorted().toList();
    }
    private List<AssumptionInput> sortedAssumptions(List<AssumptionInput> values) {
        return values.stream().sorted(Comparator.comparing(AssumptionInput::date).thenComparing(AssumptionInput::id)).toList();
    }
    private List<ShockInput> sortedShocks(List<ShockInput> values) {
        return values.stream().sorted(Comparator.comparing(ShockInput::id)).toList();
    }
    private ScenarioRequest canonicalRequest(ScenarioRequest request) {
        List<AssumptionInput> assumptions = sortedAssumptions(request.assumptions()).stream()
                .map(value -> new AssumptionInput(value.id().trim(), value.date(), money(value.amount()), value.type(),
                        value.label().trim(), value.flexible(), value.critical())).toList();
        List<ShockInput> shocks = sortedShocks(request.shocks()).stream()
                .map(value -> new ShockInput(value.id().trim(), value.type(), value.targetAssumptionId().trim(),
                        value.days(), value.amount() == null ? null : money(value.amount()),
                        value.percentage() == null ? null : value.percentage().setScale(2, RoundingMode.HALF_UP),
                        value.label().trim())).toList();
        return new ScenarioRequest(request.name().trim(), sortedDistinct(request.accountIds()), request.currency(),
                ZoneId.of(request.timeZone()).getId(), request.horizonStart(), request.horizonDays(),
                money(request.protectedMinimum()), assumptions, shocks, request.effectiveOutcomeType(),
                request.protectedScheduleId() == null ? null : request.protectedScheduleId().trim(),
                request.protectedScheduleVersion());
    }
    private String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() < 8 || key.length() > 128)
            throw new IllegalArgumentException("Idempotency-Key must contain 8 to 128 characters");
        return key.trim();
    }
    private void requireSameFingerprint(String existing, String submitted) {
        if (!Objects.equals(existing, submitted))
            throw new IllegalStateException("Idempotency-Key was already used with a different request");
    }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize Outcome Protection proof", ex); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not read Outcome Protection proof", ex); }
    }
    private <T> T read(String value, TypeReference<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not read Outcome Protection proof", ex); }
    }
    private String fingerprint(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private record Snapshot(List<LedgerAccountSnapshot> ledgerAccounts,
                            List<ScheduledCashflowSnapshot> scheduledCashflows,
                            ProtectedObligationSnapshot protectedObligation,
                            String sourceFingerprint) {}
    private record SourceFingerprint(List<LedgerAccountSnapshot> ledger,
                                     List<ScheduledCashflowSnapshot> schedules,
                                     ProtectedObligationSnapshot protectedObligation) {}
}
