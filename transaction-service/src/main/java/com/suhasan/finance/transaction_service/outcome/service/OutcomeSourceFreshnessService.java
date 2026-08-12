package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.outcome.domain.*;
import com.suhasan.finance.transaction_service.outcome.repository.*;
import com.suhasan.finance.transaction_service.outcome.service.OutcomeAuthoritativeSourceService.Snapshot;
import com.suhasan.finance.transaction_service.outcome.service.OutcomeAuthoritativeSourceService.SourceComponents;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OutcomeSourceFreshnessService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<AssumptionInput>> ASSUMPTION_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ShockInput>> SHOCK_LIST = new TypeReference<>() {};
    private static final TypeReference<List<RepairAlternative>> REPAIR_ALTERNATIVE_LIST = new TypeReference<>() {};

    private final OutcomeScenarioRepository scenarioRepository;
    private final OutcomeScenarioVersionRepository versionRepository;
    private final OutcomeSimulationResultRepository resultRepository;
    private final OutcomeGuardrailDraftRepository draftRepository;
    private final OutcomeAuthoritativeSourceService sourceService;
    private final OutcomeFreshnessRejectionRecorder rejectionRecorder;
    private final ObjectMapper objectMapper;

    public void assertFresh(OutcomeGuardrailDraft draft, String actor, String stage) {
        evaluate(draft.getScenarioId(), draft.getResultId(), draft.getGuardrailId(), draft.getUserId(),
                draft.getReplayCertificateHash(), actor, stage, null);
    }

    public void assertFresh(OutcomeGuardrailPolicy policy, String actor, String stage, String executionId) {
        OutcomeGuardrailDraft draft = draftRepository.findByGuardrailIdAndUserId(policy.getGuardrailId(), policy.getUserId())
                .orElseThrow(() -> new IllegalStateException("Guardrail draft is missing"));
        evaluate(policy.getScenarioId(), policy.getResultId(), policy.getGuardrailId(), policy.getUserId(),
                draft.getReplayCertificateHash(), actor, stage, executionId);
    }

    private void evaluate(String scenarioId, String resultId, String guardrailId, String userId,
                          String repairCertificate, String actor, String stage, String executionId) {
        OutcomeScenario scenario = scenarioRepository.findByScenarioIdAndUserId(scenarioId, userId)
                .orElseThrow(() -> new IllegalStateException("Authoritative scenario is missing"));
        OutcomeSimulationResult result = resultRepository.findById(resultId)
                .filter(candidate -> candidate.getScenarioId().equals(scenarioId))
                .orElseThrow(() -> new IllegalStateException("Authoritative simulation result is missing"));
        OutcomeScenarioVersion saved = versionRepository
                .findByScenarioIdAndScenarioVersion(scenarioId, result.getScenarioVersion())
                .orElseThrow(() -> new IllegalStateException("Authoritative scenario version is missing"));

        List<ComponentDifference> differences = new ArrayList<>();
        if (!OutcomeAuthoritativeSourceService.FINGERPRINT_SCHEMA.equals(saved.getSourceFingerprintSchema())) {
            differences.add(new ComponentDifference("FINGERPRINT_SCHEMA", hashId(saved.getVersionId()),
                    "CHANGED", List.of("schemaVersion")));
        }
        if (!savedResultContainsRepairCertificate(result, repairCertificate)) {
            differences.add(new ComponentDifference("REPAIR_CERTIFICATE", hashId(guardrailId),
                    "CHANGED", List.of("certificateHash")));
        }

        String currentFingerprint = "UNAVAILABLE";
        try {
            Snapshot current = sourceService.capture(requestFrom(scenario, saved), userId, false);
            currentFingerprint = current.sourceFingerprint();
            if (OutcomeAuthoritativeSourceService.FINGERPRINT_SCHEMA.equals(saved.getSourceFingerprintSchema())) {
                SourceComponents savedComponents = read(saved.getSourceComponentsJson(), SourceComponents.class);
                differences.addAll(diff(savedComponents, current.components()));
            }
            if (!Objects.equals(saved.getSourceFingerprint(), currentFingerprint)
                    && differences.stream().noneMatch(difference -> "CANONICAL_SOURCE".equals(difference.componentType()))) {
                differences.add(new ComponentDifference("CANONICAL_SOURCE", hashId(scenarioId),
                        "CHANGED", List.of("fingerprint")));
            }
        } catch (RuntimeException captureFailure) {
            differences.add(new ComponentDifference("AUTHORITATIVE_SOURCE", hashId(scenarioId),
                    "UNAVAILABLE_OR_CHANGED", List.of("capture")));
        }

        if (!differences.isEmpty() || !Objects.equals(saved.getSourceFingerprint(), currentFingerprint)) {
            String dedupeKey = freshnessDedupe(guardrailId, stage, executionId, currentFingerprint);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("savedSourceFingerprint", saved.getSourceFingerprint());
            evidence.put("currentSourceFingerprint", currentFingerprint);
            evidence.put("savedFingerprintSchema", saved.getSourceFingerprintSchema());
            evidence.put("currentFingerprintSchema", OutcomeAuthoritativeSourceService.FINGERPRINT_SCHEMA);
            evidence.put("differences", differences);
            evidence.put("scenarioId", scenarioId);
            evidence.put("scenarioVersion", result.getScenarioVersion());
            evidence.put("resultId", resultId);
            evidence.put("guardrailId", guardrailId);
            if (executionId != null) evidence.put("executionId", executionId);
            evidence.put("actorType", "CUSTOMER");
            evidence.put("actorIdHash", hashId(actor));
            evidence.put("stage", stage);
            evidence.put("moneyMoved", false);
            evidence.put("recoveryInstruction", ScenarioDivergedException.RECOVERY);
            rejectionRecorder.record(userId, scenarioId, result.getScenarioVersion(), resultId,
                    guardrailId, dedupeKey, evidence);
            throw new ScenarioDivergedException();
        }
    }

    private ScenarioRequest requestFrom(OutcomeScenario scenario, OutcomeScenarioVersion version) {
        ProtectedObligationSnapshot obligation = version.getProtectedObligationJson() == null ? null
                : read(version.getProtectedObligationJson(), ProtectedObligationSnapshot.class);
        OutcomeType outcomeType = version.getOutcomeType() == null
                ? OutcomeType.BALANCE_FLOOR : OutcomeType.valueOf(version.getOutcomeType());
        return new ScenarioRequest(scenario.getName(), read(version.getAccountIdsJson(), STRING_LIST),
                scenario.getCurrency(), scenario.getTimeZone(), version.getHorizonStart(), version.getHorizonDays(),
                version.getProtectedMinimum(), read(version.getAssumptionsJson(), ASSUMPTION_LIST),
                read(version.getShocksJson(), SHOCK_LIST), outcomeType,
                obligation == null ? null : obligation.scheduleId(),
                obligation == null ? null : obligation.scheduleVersion());
    }

    private List<ComponentDifference> diff(SourceComponents saved, SourceComponents current) {
        List<ComponentDifference> differences = new ArrayList<>();
        compareList("LEDGER_ACCOUNT", saved.accounts(), current.accounts(), "accountId", differences);
        compareList("SCHEDULE", saved.schedules(), current.schedules(), "scheduleId", differences);
        compareSingle("PROTECTED_OBLIGATION", saved.protectedObligation(), current.protectedObligation(),
                "scheduleId", differences);
        return differences;
    }

    private void compareList(String type, List<?> saved, List<?> current, String idField,
                             List<ComponentDifference> differences) {
        Map<String, JsonNode> savedById = index(saved, idField);
        Map<String, JsonNode> currentById = index(current, idField);
        Set<String> ids = new TreeSet<>();
        ids.addAll(savedById.keySet());
        ids.addAll(currentById.keySet());
        for (String id : ids) {
            JsonNode before = savedById.get(id);
            JsonNode after = currentById.get(id);
            if (before == null) differences.add(new ComponentDifference(type, hashId(id), "ADDED", List.of("membership")));
            else if (after == null) differences.add(new ComponentDifference(type, hashId(id), "REMOVED", List.of("membership")));
            else if (!before.equals(after)) differences.add(new ComponentDifference(type, hashId(id),
                    "CHANGED", changedFields(before, after)));
        }
    }

    private void compareSingle(String type, Object saved, Object current, String idField,
                               List<ComponentDifference> differences) {
        if (saved == null && current == null) return;
        JsonNode before = objectMapper.valueToTree(saved);
        JsonNode after = objectMapper.valueToTree(current);
        String id = before != null && before.hasNonNull(idField) ? before.get(idField).asText()
                : after != null && after.hasNonNull(idField) ? after.get(idField).asText() : type;
        if (saved == null) differences.add(new ComponentDifference(type, hashId(id), "ADDED", List.of("membership")));
        else if (current == null) differences.add(new ComponentDifference(type, hashId(id), "REMOVED", List.of("membership")));
        else if (!before.equals(after)) differences.add(new ComponentDifference(type, hashId(id),
                "CHANGED", changedFields(before, after)));
    }

    private Map<String, JsonNode> index(List<?> values, String idField) {
        Map<String, JsonNode> indexed = new TreeMap<>();
        if (values == null) return indexed;
        for (Object value : values) {
            JsonNode node = objectMapper.valueToTree(value);
            indexed.put(node.get(idField).asText(), node);
        }
        return indexed;
    }

    private List<String> changedFields(JsonNode before, JsonNode after) {
        Set<String> names = new TreeSet<>();
        before.fieldNames().forEachRemaining(names::add);
        after.fieldNames().forEachRemaining(names::add);
        return names.stream().filter(name -> !Objects.equals(before.get(name), after.get(name))).toList();
    }

    private String freshnessDedupe(String guardrailId, String stage, String executionId, String fingerprint) {
        return "scenario-diverged:" + guardrailId + ":" + stage + ":"
                + (executionId == null ? "none" : executionId) + ":" + hashId(fingerprint);
    }

    private boolean savedResultContainsRepairCertificate(OutcomeSimulationResult result, String repairCertificate) {
        if (repairCertificate == null || result.getReplayOutputJson() == null) return false;
        try {
            return read(result.getReplayOutputJson(), REPAIR_ALTERNATIVE_LIST).stream()
                    .map(RepairAlternative::certificateHash)
                    .anyMatch(repairCertificate::equals);
        } catch (RuntimeException unreadableEvidence) {
            return false;
        }
    }

    private String hashId(String value) {
        try {
            String safe = value == null ? "missing" : value;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(safe.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private <T> T read(String value, Class<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to read source evidence", ex); }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to read source evidence", ex); }
    }

    public record ComponentDifference(String componentType, String componentIdHash,
                                      String changeType, List<String> fields) {}
}
