package com.suhasan.finance.transaction_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProviderActivationService {
    private static final Set<String> BOUNDARIES =
            Set.of("NOTIFICATION", "IDP", "KMS", "RISK", "FX", "CONSENT");
    private static final Map<String, Set<String>> REQUIRED_CERTIFICATION_CHECKS = Map.of(
            "NOTIFICATION", Set.of("DELIVERY", "RECONCILIATION", "FAILURE_BEHAVIOR", "WEBHOOK_REPLAY"),
            "IDP", Set.of("CLAIM_MAPPING", "REVOCATION", "UNKNOWN_CLAIM_DENIAL", "ACCESS_REVIEW"),
            "KMS", Set.of("ROTATION", "REENCRYPTION", "KEY_VERSION_MISMATCH", "RECOVERY"),
            "RISK", Set.of("DECISION_CONSISTENCY", "TIMEOUT_FAIL_CLOSED", "MALFORMED_FAIL_CLOSED", "POLICY_PROVENANCE"),
            "FX", Set.of("FORECAST_ONLY", "PROVENANCE", "STALENESS", "RECONCILIATION"),
            "CONSENT", Set.of("VERSION_HASH", "JURISDICTION", "WITHDRAWAL", "EVIDENCE_EXPORT"));
    private static final Set<String> LOCAL_MARKERS =
            Set.of("local", "test", "demo", "unconfigured", "placeholder", "example");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public List<Map<String, Object>> list(boolean includeEvidence) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT activation_id,boundary_type,provider_alias,lifecycle_status,
                       contract_reference,credential_reference,credential_version,key_id,key_version,
                       sla_evidence_reference,reconciliation_reference,webhook_verification_reference,
                       security_review_reference,legal_approval_reference,jurisdiction_review_reference,
                       rollback_reference,disaster_recovery_reference,created_by,certified_by,approved_by,
                       activated_by,suspended_by,suspension_reason,created_at,updated_at,version
                  FROM provider_activations ORDER BY boundary_type,provider_alias
                """);
        if (!includeEvidence) {
            return rows.stream().map(this::customerView).toList();
        }
        return rows;
    }

    public Map<String, Object> detail(String activationId) {
        Map<String, Object> activation = activationForUpdate(activationId, false);
        var response = new LinkedHashMap<String, Object>(activation);
        response.put("certificationRuns", jdbc.queryForList("""
                SELECT run_id,harness_type,status,required_checks,passed_checks,failed_checks,
                       evidence_reference,health_sla_reference,rollback_reference,
                       disaster_recovery_reference,executed_by,executed_at
                  FROM provider_certification_runs WHERE activation_id=?
                 ORDER BY executed_at DESC
                """, activationId));
        response.put("events", jdbc.queryForList("""
                SELECT event_id,event_type,from_status,to_status,actor,mfa_evidence_reference,
                       mfa_verified_at,external_evidence_reference,detail,created_at
                  FROM provider_activation_events WHERE activation_id=?
                 ORDER BY created_at DESC
                """, activationId));
        return response;
    }

    @Transactional
    public Map<String, Object> create(CreateRequest request, OperatorContext operator, String idempotencyKey) {
        String boundary = requiredUpper(request.boundaryType(), "boundaryType");
        if (!BOUNDARIES.contains(boundary)) throw new IllegalArgumentException("Unsupported provider boundary");
        String providerAlias = required(request.providerAlias(), "providerAlias");
        validateReferenceOnly(request.credentialReference(), "credentialReference");
        requireMfa(operator);
        String fingerprint = fingerprint(Map.of(
                "action", "CREATE", "boundary", boundary, "providerAlias", providerAlias,
                "request", request));
        Map<String, Object> replay = replay(operator.actor(), idempotencyKey, fingerprint);
        if (replay != null) return detail(String.valueOf(replay.get("activation_id")));

        String activationId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO provider_activations
                (activation_id,boundary_type,provider_alias,lifecycle_status,contract_reference,
                 credential_reference,credential_version,key_id,key_version,sla_evidence_reference,
                 reconciliation_reference,webhook_verification_reference,security_review_reference,
                 legal_approval_reference,jurisdiction_review_reference,rollback_reference,
                 disaster_recovery_reference,created_by,created_at,updated_at)
                VALUES (?,?,?,'DRAFT',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, activationId, boundary, providerAlias, clean(request.contractReference()),
                clean(request.credentialReference()), clean(request.credentialVersion()), clean(request.keyId()),
                clean(request.keyVersion()), clean(request.slaEvidenceReference()),
                clean(request.reconciliationReference()), clean(request.webhookVerificationReference()),
                clean(request.securityReviewReference()), clean(request.legalApprovalReference()),
                clean(request.jurisdictionReviewReference()), clean(request.rollbackReference()),
                clean(request.disasterRecoveryReference()), operator.actor(), Timestamp.from(now), Timestamp.from(now));
        insertEvent(activationId, "CREATED", null, "DRAFT", operator, idempotencyKey,
                fingerprint, clean(request.contractReference()), "Provider-neutral configuration created");
        return detail(activationId);
    }

    @Transactional
    public Map<String, Object> certify(String activationId, CertificationRequest request,
                                      OperatorContext operator, String idempotencyKey) {
        requireMfa(operator);
        Map<String, Object> activation = activationForUpdate(activationId, true);
        requireStatus(activation, "DRAFT");
        requireDifferent(operator.actor(), activation.get("created_by"),
                "Sandbox certification must be performed by a different operator");
        String boundary = String.valueOf(activation.get("boundary_type"));
        Set<String> requiredChecks = REQUIRED_CERTIFICATION_CHECKS.get(boundary);
        Set<String> passed = normalizedChecks(request.passedChecks());
        Set<String> failed = new TreeSet<>(requiredChecks);
        failed.removeAll(passed);
        String status = failed.isEmpty() ? "PASSED" : "FAILED";
        requireReference(request.evidenceReference(), "certification evidence reference");
        requireReference(request.healthSlaReference(), "health/SLA evidence reference");
        requireReference(request.rollbackReference(), "rollback exercise reference");
        requireReference(request.disasterRecoveryReference(), "disaster-recovery exercise reference");
        String fingerprint = fingerprint(Map.of("action", "CERTIFY", "activationId", activationId,
                "request", request));
        Map<String, Object> replay = replay(operator.actor(), idempotencyKey, fingerprint);
        if (replay != null) return detail(activationId);

        jdbc.update("""
                INSERT INTO provider_certification_runs
                (run_id,activation_id,harness_type,status,required_checks,passed_checks,failed_checks,
                 evidence_reference,health_sla_reference,rollback_reference,disaster_recovery_reference,
                 executed_by,executed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), activationId, boundary, status, csv(requiredChecks),
                csv(passed), csv(failed), request.evidenceReference().trim(),
                request.healthSlaReference().trim(), request.rollbackReference().trim(),
                request.disasterRecoveryReference().trim(), operator.actor(), Timestamp.from(Instant.now()));
        if (!failed.isEmpty()) {
            insertEvent(activationId, "CERTIFICATION_FAILED", "DRAFT", "DRAFT", operator,
                    idempotencyKey, fingerprint, request.evidenceReference(),
                    "Required checks failed: " + csv(failed));
            return detail(activationId);
        }
        transition(activationId, "SANDBOX_CERTIFIED", "certified_by", operator.actor());
        insertEvent(activationId, "SANDBOX_CERTIFIED", "DRAFT", "SANDBOX_CERTIFIED", operator,
                idempotencyKey, fingerprint, request.evidenceReference(),
                "Provider-neutral sandbox certification passed");
        return detail(activationId);
    }

    @Transactional
    public Map<String, Object> approve(String activationId, ApprovalRequest request,
                                      OperatorContext operator, String idempotencyKey) {
        requireMfa(operator);
        Map<String, Object> activation = activationForUpdate(activationId, true);
        requireStatus(activation, "SANDBOX_CERTIFIED");
        requireDifferent(operator.actor(), activation.get("created_by"),
                "Approval must be separated from configuration");
        requireDifferent(operator.actor(), activation.get("certified_by"),
                "Approval must be separated from certification");
        requireReference(request.externalApprovalReference(), "external approval reference");
        requireReference(request.securityReviewReference(), "security review reference");
        String boundary = String.valueOf(activation.get("boundary_type"));
        if (Set.of("FX", "CONSENT", "RISK").contains(boundary)) {
            requireReference(request.legalApprovalReference(), "legal/compliance approval reference");
            requireReference(request.jurisdictionReviewReference(), "jurisdiction review reference");
        }
        String fingerprint = fingerprint(Map.of("action", "APPROVE", "activationId", activationId,
                "request", request));
        Map<String, Object> replay = replay(operator.actor(), idempotencyKey, fingerprint);
        if (replay != null) return detail(activationId);
        jdbc.update("""
                UPDATE provider_activations SET lifecycle_status='APPROVED',approved_by=?,
                       security_review_reference=?,legal_approval_reference=?,
                       jurisdiction_review_reference=?,updated_at=?,version=version+1
                 WHERE activation_id=?
                """, operator.actor(), request.securityReviewReference().trim(),
                clean(request.legalApprovalReference()), clean(request.jurisdictionReviewReference()),
                Timestamp.from(Instant.now()), activationId);
        insertEvent(activationId, "EXTERNALLY_APPROVED", "SANDBOX_CERTIFIED", "APPROVED",
                operator, idempotencyKey, fingerprint, request.externalApprovalReference(),
                "External evidence was recorded; code did not manufacture approval");
        return detail(activationId);
    }

    @Transactional
    public Map<String, Object> activate(String activationId, ActivationRequest request,
                                       OperatorContext operator, String idempotencyKey) {
        requireMfa(operator);
        Map<String, Object> activation = activationForUpdate(activationId, true);
        requireStatus(activation, "APPROVED");
        requireDifferent(operator.actor(), activation.get("approved_by"),
                "Production activation must be separated from approval");
        if (!environment.getProperty("integration.activation.production-enabled", Boolean.class, false)
                || !Arrays.asList(environment.getActiveProfiles()).contains("production")) {
            throw new IllegalStateException("Production activation is disabled outside an explicitly enabled production profile");
        }
        validateProductionReferences(activation);
        requireReference(request.changeReference(), "production change reference");
        String fingerprint = fingerprint(Map.of("action", "ACTIVATE", "activationId", activationId,
                "request", request));
        Map<String, Object> replay = replay(operator.actor(), idempotencyKey, fingerprint);
        if (replay != null) return detail(activationId);
        transition(activationId, "ACTIVE", "activated_by", operator.actor());
        insertEvent(activationId, "PRODUCTION_ACTIVATED", "APPROVED", "ACTIVE", operator,
                idempotencyKey, fingerprint, request.changeReference(),
                "Production control-plane activation only; no customer funds or executable FX");
        return detail(activationId);
    }

    @Transactional
    public Map<String, Object> suspend(String activationId, SuspensionRequest request,
                                      OperatorContext operator, String idempotencyKey) {
        requireMfa(operator);
        Map<String, Object> activation = activationForUpdate(activationId, true);
        String from = String.valueOf(activation.get("lifecycle_status"));
        if (!Set.of("ACTIVE", "APPROVED", "SANDBOX_CERTIFIED").contains(from)) {
            throw new IllegalStateException("Only certified, approved, or active providers can be suspended");
        }
        String reason = required(request.reason(), "suspension reason");
        String fingerprint = fingerprint(Map.of("action", "SUSPEND", "activationId", activationId,
                "reason", reason));
        Map<String, Object> replay = replay(operator.actor(), idempotencyKey, fingerprint);
        if (replay != null) return detail(activationId);
        jdbc.update("""
                UPDATE provider_activations SET lifecycle_status='SUSPENDED',suspended_by=?,
                       suspension_reason=?,updated_at=?,version=version+1 WHERE activation_id=?
                """, operator.actor(), sanitize(reason), Timestamp.from(Instant.now()), activationId);
        insertEvent(activationId, "EMERGENCY_SUSPENDED", from, "SUSPENDED", operator,
                idempotencyKey, fingerprint, clean(request.incidentReference()),
                "Emergency suspension blocks provider use and never reverses completed ledger history");
        return detail(activationId);
    }

    private Map<String, Object> activationForUpdate(String activationId, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM provider_activations WHERE activation_id=?" + suffix, activationId);
        if (rows.isEmpty()) throw new NoSuchElementException("Provider activation was not found");
        return rows.getFirst();
    }

    private void transition(String activationId, String status, String actorColumn, String actor) {
        if (!Set.of("certified_by", "activated_by").contains(actorColumn)) {
            throw new IllegalArgumentException("Unsupported lifecycle actor");
        }
        jdbc.update("UPDATE provider_activations SET lifecycle_status=?," + actorColumn
                        + "=?,updated_at=?,version=version+1 WHERE activation_id=?",
                status, actor, Timestamp.from(Instant.now()), activationId);
    }

    private void insertEvent(String activationId, String eventType, String from, String to,
                             OperatorContext operator, String idempotencyKey, String fingerprint,
                             String evidenceReference, String detail) {
        jdbc.update("""
                INSERT INTO provider_activation_events
                (event_id,activation_id,event_type,from_status,to_status,actor,idempotency_key,
                 request_fingerprint,mfa_evidence_reference,mfa_verified_at,
                 external_evidence_reference,detail,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), activationId, eventType, from, to, operator.actor(),
                requireIdempotency(idempotencyKey), fingerprint, operator.mfaEvidenceReference().trim(),
                Timestamp.from(operator.mfaVerifiedAt()), clean(evidenceReference), sanitize(detail),
                Timestamp.from(Instant.now()));
    }

    private Map<String, Object> replay(String actor, String idempotencyKey, String fingerprint) {
        String key = requireIdempotency(idempotencyKey);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT activation_id,request_fingerprint FROM provider_activation_events
                 WHERE actor=? AND idempotency_key=?
                """, actor, key);
        if (rows.isEmpty()) return null;
        if (!fingerprint.equals(rows.getFirst().get("request_fingerprint"))) {
            throw new IllegalStateException("Idempotency-Key was reused for a different provider activation request");
        }
        return rows.getFirst();
    }

    private void requireMfa(OperatorContext operator) {
        if (operator == null || blank(operator.actor()) || blank(operator.mfaEvidenceReference())
                || operator.mfaVerifiedAt() == null) {
            throw new IllegalArgumentException("Explicit operator MFA evidence is required");
        }
        Instant now = Instant.now();
        if (operator.mfaVerifiedAt().isAfter(now.plusSeconds(30))
                || Duration.between(operator.mfaVerifiedAt(), now).abs().toMinutes() > 5) {
            throw new IllegalArgumentException("Operator MFA evidence must be recent");
        }
        validateReferenceOnly(operator.mfaEvidenceReference(), "operator MFA evidence reference");
    }

    private void validateProductionReferences(Map<String, Object> activation) {
        for (String field : List.of("contract_reference", "credential_reference",
                "security_review_reference", "sla_evidence_reference", "rollback_reference",
                "disaster_recovery_reference")) {
            requireProductionReference(activation.get(field), field);
        }
        String boundary = String.valueOf(activation.get("boundary_type"));
        if ("KMS".equals(boundary)) {
            requireProductionReference(activation.get("key_id"), "key_id");
            requireProductionReference(activation.get("key_version"), "key_version");
        }
        if (Set.of("FX", "CONSENT", "RISK").contains(boundary)) {
            requireProductionReference(activation.get("legal_approval_reference"), "legal_approval_reference");
            requireProductionReference(activation.get("jurisdiction_review_reference"), "jurisdiction_review_reference");
        }
        String credential = String.valueOf(activation.get("credential_reference"));
        if (!(credential.startsWith("vault://") || credential.startsWith("secret://")
                || credential.startsWith("kms://"))) {
            throw new IllegalStateException("Production credentials must use an approved external secret reference");
        }
    }

    private void requireProductionReference(Object value, String name) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank() || LOCAL_MARKERS.stream().anyMatch(marker ->
                text.toLowerCase(Locale.ROOT).contains(marker))) {
            throw new IllegalStateException("Production reference is missing or non-production: " + name);
        }
    }

    private Map<String, Object> customerView(Map<String, Object> row) {
        return Map.of(
                "boundaryType", row.get("boundary_type"),
                "lifecycleStatus", row.get("lifecycle_status"),
                "configured", true,
                "sandboxCertified", List.of("SANDBOX_CERTIFIED", "APPROVED", "ACTIVE")
                        .contains(String.valueOf(row.get("lifecycle_status"))),
                "externallyApproved", List.of("APPROVED", "ACTIVE")
                        .contains(String.valueOf(row.get("lifecycle_status"))),
                "productionActive", "ACTIVE".equals(row.get("lifecycle_status")));
    }

    private Set<String> normalizedChecks(Collection<String> checks) {
        if (checks == null) return Set.of();
        var result = new TreeSet<String>();
        checks.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank())
                .map(v -> v.toUpperCase(Locale.ROOT)).forEach(result::add);
        return result;
    }

    private String fingerprint(Object value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not fingerprint provider activation request", failure);
        }
    }

    private String csv(Collection<String> values) {
        return values.stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
    }

    private String requireIdempotency(String value) {
        String key = required(value, "Idempotency-Key");
        if (key.length() > 128) throw new IllegalArgumentException("Idempotency-Key is too long");
        return key;
    }

    private void requireStatus(Map<String, Object> activation, String expected) {
        if (!expected.equals(activation.get("lifecycle_status"))) {
            throw new IllegalStateException("Provider activation must be " + expected);
        }
    }

    private void requireDifferent(String actor, Object prior, String message) {
        if (actor.equals(String.valueOf(prior))) throw new IllegalStateException(message);
    }

    private void requireReference(String value, String name) {
        required(value, name);
        validateReferenceOnly(value, name);
    }

    private void validateReferenceOnly(String value, String name) {
        if (value == null) return;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("-----begin") || normalized.contains("bearer ")
                || normalized.matches(".*\\b(sk|pk)_[a-z0-9]{16,}.*")
                || normalized.contains("password=")) {
            throw new IllegalArgumentException(name + " must contain an external reference, never credential material");
        }
    }

    private String requiredUpper(String value, String name) {
        return required(value, name).toUpperCase(Locale.ROOT);
    }

    private String required(String value, String name) {
        if (blank(value)) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return blank(value) ? null : sanitize(value);
    }

    private String sanitize(String value) {
        if (value == null) return null;
        String clean = value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= 1000 ? clean : clean.substring(0, 1000);
    }

    public record OperatorContext(String actor, String mfaEvidenceReference, Instant mfaVerifiedAt) {}
    public record CreateRequest(String boundaryType, String providerAlias, String contractReference,
                                String credentialReference, String credentialVersion, String keyId,
                                String keyVersion, String slaEvidenceReference,
                                String reconciliationReference, String webhookVerificationReference,
                                String securityReviewReference, String legalApprovalReference,
                                String jurisdictionReviewReference, String rollbackReference,
                                String disasterRecoveryReference) {}
    public record CertificationRequest(Set<String> passedChecks, String evidenceReference,
                                       String healthSlaReference, String rollbackReference,
                                       String disasterRecoveryReference) {}
    public record ApprovalRequest(String externalApprovalReference, String securityReviewReference,
                                  String legalApprovalReference, String jurisdictionReviewReference) {}
    public record ActivationRequest(String changeReference) {}
    public record SuspensionRequest(String reason, String incidentReference) {}
}
