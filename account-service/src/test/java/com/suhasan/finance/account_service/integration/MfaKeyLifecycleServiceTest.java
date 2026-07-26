package com.suhasan.finance.account_service.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MfaKeyLifecycleServiceTest {
    private JdbcTemplate jdbc;
    private MfaSecretManager secrets;
    private MfaKeyLifecycleService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        secrets = mock(MfaSecretManager.class);
        service = new MfaKeyLifecycleService(jdbc, secrets);
        when(secrets.health()).thenReturn(new MfaSecretManager.Health(
                "external-kms", "v2", true, true, "HEALTHY", Instant.now(), "evidence"));
    }

    @Test
    void requiresRequestIdAndReplaysExistingRun() {
        assertThatThrownBy(() -> service.rotate("operator", " "))
                .isInstanceOf(IllegalArgumentException.class);
        Map<String, Object> existing = Map.of("status", "COMPLETED");
        when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(List.of(existing));
        assertThat(service.rotate("operator", "request-1")).isSameAs(existing);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void completesEmptyRotationAndReturnsAuditableRun() {
        when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(List.of());
        when(jdbc.queryForMap(anyString(), any(Object.class))).thenReturn(Map.of(
                "status", "COMPLETED", "examined_count", 0));
        Map<String, Object> result = service.rotate("operator", "request-empty");
        assertThat(result).containsEntry("status", "COMPLETED")
                .containsEntry("examined_count", 0);
    }

    @Test
    void rotatesAvailableRowsAndRecordsIndividualFailures() {
        Map<String, Object> ok = Map.of("id", 1L, "secret_ciphertext", "cipher-1", "secret_key_id", "v1");
        Map<String, Object> bad = Map.of("id", 2L, "secret_ciphertext", "cipher-2", "secret_key_id", "missing");
        when(jdbc.queryForList(anyString(), any(Object.class)))
                .thenReturn(List.of(), List.of(ok, bad));
        when(secrets.decrypt("cipher-1", "v1")).thenReturn("plain");
        when(secrets.encrypt("plain")).thenReturn(new MfaSecretManager.Ciphertext("cipher-v2", "v2"));
        when(secrets.decrypt("cipher-2", "missing")).thenThrow(new IllegalStateException("missing key"));
        when(jdbc.queryForMap(anyString(), any(Object.class))).thenReturn(Map.of(
                "status", "PARTIAL_FAILED", "examined_count", 2, "rotated_count", 1, "failed_count", 1));

        Map<String, Object> result = service.rotate("operator", "request-mixed");

        assertThat(result).containsEntry("status", "PARTIAL_FAILED")
                .containsEntry("rotated_count", 1)
                .containsEntry("failed_count", 1);
        verify(secrets).encrypt("plain");
    }

    @Test
    void failsClosedWhenRowQueryFailsAndStillFinalizesEvidence() {
        when(jdbc.queryForList(anyString(), any(Object.class)))
                .thenReturn(List.of())
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service.rotate("operator", "request-db-failure"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");
        verify(jdbc).update(
                org.mockito.ArgumentMatchers.contains("UPDATE mfa_key_rotation_runs"),
                any(Object[].class));
    }

    @Test
    void healthExposesKmsAndBoundedRotationEvidence() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("status", "COMPLETED")));
        Map<String, Object> result = service.health();
        assertThat(result).containsEntry("secretsLogged", false);
        assertThat((List<?>) result.get("rotationRuns")).hasSize(1);
    }
}
