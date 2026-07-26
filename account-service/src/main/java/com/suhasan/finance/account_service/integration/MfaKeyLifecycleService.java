package com.suhasan.finance.account_service.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MfaKeyLifecycleService {
    private final JdbcTemplate jdbc;
    private final MfaSecretManager secrets;

    @Transactional
    public Map<String, Object> rotate(final String requestedBy, final String requestId) {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("X-Operator-Request-Id is required");
        final String target = secrets.health().activeKeyId();
        final String runId = UUID.nameUUIDFromBytes(("mfa-rotation:" + requestId.trim())
                .getBytes(StandardCharsets.UTF_8)).toString();
        final var existing = jdbc.queryForList("SELECT * FROM mfa_key_rotation_runs WHERE run_id=?", runId);
        if (!existing.isEmpty()) return existing.getFirst();

        final Instant started = Instant.now();
        int examined = 0;
        int rotated = 0;
        int failed = 0;
        String failureReason = null;
        jdbc.update("""
                INSERT INTO mfa_key_rotation_runs
                (run_id,requested_by,from_key_id,to_key_id,status,examined_count,rotated_count,failed_count,started_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, runId, requestedBy, "mixed", target, "RUNNING", 0, 0, 0, started);
        try {
            final var rows = jdbc.queryForList("""
                    SELECT id, secret_ciphertext, secret_key_id FROM user_mfa_methods
                    WHERE secret_key_id <> ? FOR UPDATE
                    """, target);
            examined = rows.size();
            for (final var row : rows) {
                try {
                    final String plaintext = secrets.decrypt(String.valueOf(row.get("secret_ciphertext")),
                            String.valueOf(row.get("secret_key_id")));
                    final var encrypted = secrets.encrypt(plaintext);
                    jdbc.update("UPDATE user_mfa_methods SET secret_ciphertext=?, secret_key_id=? WHERE id=?",
                            encrypted.value(), encrypted.keyId(), row.get("id"));
                    rotated++;
                } catch (RuntimeException failure) {
                    failed++;
                    failureReason = "One or more key versions were unavailable or ciphertext validation failed";
                }
            }
        } catch (RuntimeException failure) {
            failureReason = "Rotation failed closed; inspect KMS health and database evidence";
            throw failure;
        } finally {
            final String status = failed == 0 ? "COMPLETED" : "PARTIAL_FAILED";
            jdbc.update("""
                    UPDATE mfa_key_rotation_runs SET status=?,examined_count=?,rotated_count=?,failed_count=?,
                    completed_at=?,failure_reason=? WHERE run_id=?
                    """, status, examined, rotated, failed, Instant.now(), failureReason, runId);
        }
        return jdbc.queryForMap("SELECT * FROM mfa_key_rotation_runs WHERE run_id=?", runId);
    }

    public Map<String, Object> health() {
        return Map.of("kms", secrets.health(),
                "secretsLogged", false,
                "rotationRuns", jdbc.queryForList("""
                        SELECT run_id,requested_by,from_key_id,to_key_id,status,examined_count,
                               rotated_count,failed_count,started_at,completed_at,failure_reason
                        FROM mfa_key_rotation_runs ORDER BY started_at DESC LIMIT 20
                        """));
    }
}
