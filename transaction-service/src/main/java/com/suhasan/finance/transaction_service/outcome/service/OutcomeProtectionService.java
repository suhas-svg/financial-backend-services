package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.entity.*;
import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.*;
import com.suhasan.finance.transaction_service.outcome.domain.*;
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

    private final OutcomeScenarioRepository scenarioRepository;
    private final OutcomeScenarioVersionRepository versionRepository;
    private final OutcomeSimulationResultRepository resultRepository;
    private final OutcomeGuardrailDraftRepository guardrailRepository;
    private final OutcomeDomainEventRepository eventRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBalanceProjectionRepository projectionRepository;
    private final ScheduledTransferRepository scheduledTransferRepository;
    private final OutcomeSimulationEngine simulationEngine;
    private final ResilientAccountServiceClient accountServiceClient;
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

        Snapshot snapshot = captureSnapshot(request, userId);
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

        Snapshot snapshot = captureSnapshot(request, userId);
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
            return new ScenarioSummary(scenario.getScenarioId(), scenario.getName(), scenario.getCurrentVersion(),
                    scenario.getStatus(), scenario.getCurrency(), version.getHorizonStart(), version.getHorizonDays(),
                    version.getProtectedMinimum(), result.isBaselineSafe(), scenario.getUpdatedAt());
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
    public void acknowledgeWarning(String eventId, String userId, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        OutcomeDomainEvent warning = eventRepository.findByEventIdAndUserId(eventId, userId)
                .filter(event -> "OUTCOME_PROTECTION_AT_RISK".equals(event.getEventType()))
                .orElseThrow(() -> new AccessDeniedException("Warning not found"));
        OutcomeScenario scenario = ownedScenario(warning.getScenarioId(), userId);
        recordEvent("WARNING_ACKNOWLEDGED", scenario, warning.getResultId(), null,
                "warning-ack:" + warning.getEventId(),
                Map.of("warningEventId", warning.getEventId(), "idempotencyKey", key));
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
        ScenarioRequest request = requestFrom(scenario, saved);
        Snapshot fresh = captureSnapshot(request, scenario.getUserId());
        SimulationProof simulation = simulate(request, fresh);
        String previousFingerprint = scenario.getLastSourceFingerprint();
        String previousState = scenario.getLastProtectionState();
        boolean diverged = !fresh.sourceFingerprint().equals(saved.getSourceFingerprint());
        boolean atRisk = !simulation.baseline().safe();
        boolean notificationEmitted = false;

        if (atRisk && !"AT_RISK".equals(previousState)) {
            String warningDedupe = "outcome-risk:" + scenario.getScenarioId() + ":" + fresh.sourceFingerprint();
            OutcomeDomainEvent warning = recordEvent("OUTCOME_PROTECTION_AT_RISK", scenario, null, null,
                    warningDedupe, Map.of("failureDate", String.valueOf(simulation.baseline().failureDate()),
                            "lowestBalance", simulation.baseline().lowestBalance(),
                            "protectedMinimum", simulation.baseline().protectedMinimum(),
                            "sourceFingerprint", fresh.sourceFingerprint()));
            notificationEmitted = emitRiskNotification(scenario, simulation, warning.getEventId());
        }
        scenario.setLastSourceFingerprint(fresh.sourceFingerprint());
        scenario.setLastProtectionState(atRisk ? "AT_RISK" : "SAFE");
        scenario.setLastCheckedAt(Instant.now());
        scenarioRepository.save(scenario);
        return new DivergenceResponse(scenario.getScenarioId(), previousFingerprint, fresh.sourceFingerprint(),
                diverged, atRisk, notificationEmitted, simulation, scenario.getLastCheckedAt());
    }

    private ScenarioResponse persistVersionAndResult(OutcomeScenario scenario, int number, ScenarioRequest request,
                                                     Snapshot snapshot, SimulationProof simulation,
                                                     String mutationKey, String requestFingerprint) {
        OutcomeScenarioVersion version = OutcomeScenarioVersion.builder()
                .versionId(UUID.randomUUID().toString()).scenarioId(scenario.getScenarioId()).scenarioVersion(number)
                .horizonStart(request.horizonStart()).horizonDays(request.horizonDays())
                .protectedMinimum(money(request.protectedMinimum())).accountIdsJson(json(sortedDistinct(request.accountIds())))
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
                .resultFingerprint(fingerprint(simulation)).build();
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
        for (RepairAction action : simulation.repair().selectedRepairs()) {
            guardrailRepository.save(OutcomeGuardrailDraft.builder()
                    .guardrailId(UUID.randomUUID().toString()).scenarioId(scenario.getScenarioId())
                    .resultId(result.getResultId()).userId(scenario.getUserId()).guardrailType(action.type())
                    .thresholdAmount(action.amount()).currency(scenario.getCurrency()).scopeJson(json(scope))
                    .previewText(action.explanation() + " This is a read-only recommendation and will not move or schedule money.")
                    .expiresAt(expiresAt).build());
        }
    }

    private Snapshot captureSnapshot(ScenarioRequest request, String userId) {
        Instant capturedAt = Instant.now();
        List<String> accountIds = sortedDistinct(request.accountIds());
        List<LedgerAccountSnapshot> ledger = new ArrayList<>();
        for (String accountId : accountIds) {
            LedgerAccount account = ledgerAccountRepository.findByExternalAccountId(accountId)
                    .filter(candidate -> candidate.getAccountKind() == LedgerAccountKind.CUSTOMER)
                    .orElseThrow(() -> new IllegalArgumentException("Authoritative ledger account %s was not found".formatted(accountId)));
            if (!userId.equals(account.getOwnerId())) throw new AccessDeniedException("Selected ledger account is not owned by the authenticated customer");
            if (!request.currency().equals(account.getCurrency().trim())) throw new IllegalArgumentException("All selected ledger accounts must use the scenario currency");
            LedgerBalanceProjection projection = projectionRepository.findById(account.getLedgerAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Authoritative balance projection was not found"));
            Instant projectionTime = projection.getUpdatedAt() == null ? capturedAt : projection.getUpdatedAt().toInstant(ZoneOffset.UTC);
            ledger.add(new LedgerAccountSnapshot(accountId, request.currency(), money(projection.getAvailableBalance()),
                    projection.getProjectionVersion(), projectionTime));
        }
        List<ScheduledCashflowSnapshot> schedules = captureSchedules(request, userId, new LinkedHashSet<>(accountIds));
        String sourceFingerprint = fingerprint(new SourceFingerprint(ledger, schedules));
        return new Snapshot(ledger, schedules, sourceFingerprint);
    }

    private List<ScheduledCashflowSnapshot> captureSchedules(ScenarioRequest request, String userId, Set<String> selectedAccounts) {
        ZoneId zone = ZoneId.of(request.timeZone());
        LocalDate end = request.horizonStart().plusDays(request.horizonDays() - 1L);
        List<ScheduledCashflowSnapshot> events = new ArrayList<>();
        for (ScheduledTransfer schedule : scheduledTransferRepository
                .findByUserIdAndStatusOrderByNextRunAtAsc(userId, ScheduledTransferStatus.ACTIVE)) {
            if (!request.currency().equals(schedule.getCurrency())) continue;
            boolean outgoing = selectedAccounts.contains(schedule.getFromAccountId());
            boolean incoming = selectedAccounts.contains(schedule.getToAccountId());
            BigDecimal signed = (outgoing ? schedule.getAmount().negate() : BigDecimal.ZERO)
                    .add(incoming ? schedule.getAmount() : BigDecimal.ZERO);
            if (signed.signum() == 0) continue;
            Instant occurrence = schedule.getNextRunAt();
            int guard = 0;
            while (occurrence != null && guard++ < 100) {
                LocalDate date = occurrence.atZone(zone).toLocalDate();
                if (date.isAfter(end) || (schedule.getEndAt() != null && occurrence.isAfter(schedule.getEndAt()))) break;
                if (!date.isBefore(request.horizonStart())) {
                    events.add(new ScheduledCashflowSnapshot(
                            "schedule:" + schedule.getScheduleId() + ":" + occurrence,
                            schedule.getScheduleId(), date, money(signed),
                            schedule.getDescription() == null || schedule.getDescription().isBlank()
                                    ? "Scheduled transfer" : schedule.getDescription(),
                            schedule.getFromAccountId(), schedule.getToAccountId()));
                }
                if (schedule.getScheduleType() == ScheduledTransferType.ONE_TIME) break;
                occurrence = com.suhasan.finance.transaction_service.service.ScheduledTransferService
                        .nextRunAfter(occurrence, schedule.getFrequency());
            }
        }
        return events.stream().sorted(Comparator.comparing(ScheduledCashflowSnapshot::date)
                .thenComparing(ScheduledCashflowSnapshot::eventId)).toList();
    }

    private SimulationProof simulate(ScenarioRequest request, Snapshot snapshot) {
        List<OutcomeSimulationEngine.Cashflow> cashflows = new ArrayList<>();
        for (AssumptionInput assumption : sortedAssumptions(request.assumptions())) {
            cashflows.add(new OutcomeSimulationEngine.Cashflow(assumption.id(), assumption.date(), money(assumption.amount()),
                    "ASSUMPTION", assumption.label(), assumption.flexible(), assumption.critical()));
        }
        for (ScheduledCashflowSnapshot schedule : snapshot.scheduledCashflows()) {
            cashflows.add(new OutcomeSimulationEngine.Cashflow(schedule.eventId(), schedule.date(), schedule.amount(),
                    "SCHEDULED_TRANSFER", schedule.label(), false, true));
        }
        BigDecimal starting = snapshot.ledgerAccounts().stream().map(LedgerAccountSnapshot::availableBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return simulationEngine.simulate(money(starting), money(request.protectedMinimum()),
                request.horizonStart(), request.horizonDays(), cashflows, sortedShocks(request.shocks()));
    }

    private ScenarioResponse response(OutcomeScenario scenario) { return response(scenario, scenario.getCurrentVersion()); }

    private ScenarioResponse response(OutcomeScenario scenario, int number) {
        OutcomeScenarioVersion version = requiredVersion(scenario, number);
        OutcomeSimulationResult result = requiredResult(scenario.getScenarioId(), number);
        List<String> accountIds = read(version.getAccountIdsJson(), STRING_LIST);
        List<LedgerAccountSnapshot> ledger = read(version.getLedgerSnapshotJson(), LEDGER_LIST);
        List<ScheduledCashflowSnapshot> schedules = read(version.getScheduleSnapshotJson(), SCHEDULE_LIST);
        BigDecimal starting = ledger.stream().map(LedgerAccountSnapshot::availableBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        SourceSnapshot source = new SourceSnapshot(money(starting), ledger, schedules, version.getSourceFingerprint());
        List<GuardrailResponse> guardrails = guardrailRepository.findByResultIdOrderByCreatedAtAsc(result.getResultId())
                .stream().map(this::guardrailResponse).toList();
        return new ScenarioResponse(scenario.getScenarioId(), scenario.getName(), number, scenario.getStatus(),
                scenario.getCurrency(), scenario.getTimeZone(), version.getHorizonStart(), version.getHorizonDays(),
                version.getProtectedMinimum(), accountIds, read(version.getAssumptionsJson(), ASSUMPTION_LIST),
                read(version.getShocksJson(), SHOCK_LIST), source, read(result.getProofJson(), SimulationProof.class),
                guardrails, version.getCreatedAt());
    }

    private GuardrailResponse guardrailResponse(OutcomeGuardrailDraft draft) {
        return new GuardrailResponse(draft.getGuardrailId(), draft.getGuardrailType(), draft.getThresholdAmount(),
                draft.getCurrency(), read(draft.getScopeJson(), STRING_LIST), draft.getExpiresAt(), draft.getStatus(),
                draft.getPreviewText(), draft.getAcceptedAt());
    }

    private ScenarioRequest requestFrom(OutcomeScenario scenario, OutcomeScenarioVersion version) {
        return new ScenarioRequest(scenario.getName(), read(version.getAccountIdsJson(), STRING_LIST),
                scenario.getCurrency(), scenario.getTimeZone(), version.getHorizonStart(), version.getHorizonDays(),
                version.getProtectedMinimum(), read(version.getAssumptionsJson(), ASSUMPTION_LIST),
                read(version.getShocksJson(), SHOCK_LIST));
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

    private boolean emitRiskNotification(OutcomeScenario scenario, SimulationProof simulation, String warningEventId) {
        try {
            accountServiceClient.createNotification(ResilientAccountServiceClient.NotificationRequest.builder()
                    .userId(scenario.getUserId()).type("OUTCOME_PROTECTION_AT_RISK").severity("WARNING")
                    .title("Balance Shield needs attention")
                    .message("Your saved outcome may fall below %s %s on %s. Review the causal timeline and guardrail drafts."
                            .formatted(scenario.getCurrency(), simulation.baseline().protectedMinimum().toPlainString(),
                                    simulation.baseline().failureDate()))
                    .sourceType("OUTCOME_PROTECTION").sourceId(scenario.getScenarioId())
                    .dedupeKey("outcome-protection:" + warningEventId).build());
            return true;
        } catch (RuntimeException ex) {
            log.warn("Best-effort Outcome Protection notification failed for scenario {}: {}",
                    scenario.getScenarioId(), ex.getMessage());
            return false;
        }
    }

    private void validateRequest(ScenarioRequest request) {
        ZoneId.of(request.timeZone());
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
                money(request.protectedMinimum()), assumptions, shocks);
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
                            String sourceFingerprint) {}
    private record SourceFingerprint(List<LedgerAccountSnapshot> ledger,
                                     List<ScheduledCashflowSnapshot> schedules) {}
}
