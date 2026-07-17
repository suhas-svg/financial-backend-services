package com.suhasan.finance.transaction_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsentGovernanceService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> versions() {
        return jdbc.queryForList("""
                SELECT version_id, terms_hash, lifecycle_status, jurisdiction_rules,
                       accessibility_standard, retention_policy, approval_reference,
                       effective_from, retired_at, created_at
                  FROM outcome_consent_versions ORDER BY created_at DESC
                """);
    }

    public List<Map<String, Object>> evidence(String userId) {
        return jdbc.queryForList("""
                SELECT event_id, policy_id, version_id, event_type, jurisdiction,
                       accessibility_metadata, retention_metadata, complaint_reference,
                       detail, created_at
                  FROM outcome_consent_governance_events
                 WHERE user_id = ? ORDER BY created_at DESC
                """, userId);
    }

    @Transactional
    public Map<String, Object> record(String userId, String type, GovernanceRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        if (!List.of("WITHDRAWN", "COMPLAINT_RECORDED", "EVIDENCE_EXPORTED").contains(type)) {
            throw new IllegalArgumentException("Unsupported consent governance event");
        }
        String fingerprint = hash(json(Map.of(
                "type", type,
                "policyId", safe(request.policyId()),
                "versionId", safe(request.versionId()),
                "jurisdiction", safe(request.jurisdiction()),
                "detail", safe(request.detail()))));
        String eventId = UUID.randomUUID().toString();
        int inserted = jdbc.update("""
                INSERT INTO outcome_consent_governance_events
                (event_id,user_id,policy_id,version_id,event_type,idempotency_key,request_fingerprint,
                 jurisdiction,accessibility_metadata,retention_metadata,complaint_reference,detail,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (user_id,idempotency_key) DO NOTHING
                """, eventId, userId, blankToNull(request.policyId()), blankToNull(request.versionId()), type,
                idempotencyKey.trim(), fingerprint, blankToNull(request.jurisdiction()),
                "Customer-readable JSON evidence", "Deployment retention policy applies",
                type.equals("COMPLAINT_RECORDED") ? "complaint-" + eventId : null,
                sanitize(request.detail()), Timestamp.from(java.time.Instant.now()));
        if (inserted == 0) {
            Map<String, Object> existing = jdbc.queryForMap("""
                    SELECT event_id, request_fingerprint FROM outcome_consent_governance_events
                    WHERE user_id=? AND idempotency_key=?
                    """, userId, idempotencyKey.trim());
            if (!fingerprint.equals(existing.get("request_fingerprint"))) {
                throw new IllegalStateException("Idempotency-Key was reused for different consent evidence");
            }
        }
        return Map.of("userId", userId, "eventType", type, "evidence", evidence(userId));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not fingerprint consent evidence", ex);
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not fingerprint consent evidence", ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String sanitize(String value) {
        if (value == null) return null;
        String safe = value.replace('\r', ' ').replace('\n', ' ').trim();
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }

    public record GovernanceRequest(String policyId, String versionId, String jurisdiction, String detail) {}
}
