package com.suhasan.finance.transaction_service.outcome.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "LEDGER_TEST_JDBC_URL", matches = ".+")
class OutcomeProtectionMigrationSmokeTest {
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        String url = System.getenv("LEDGER_TEST_JDBC_URL");
        String user = System.getenv().getOrDefault("LEDGER_TEST_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("LEDGER_TEST_DB_PASSWORD", "test");
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    }

    @Test
    void createsAllProofTablesAndRejectsHistoricalVersionMutation() {
        Integer tableCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('outcome_scenarios', 'outcome_scenario_versions',
                                     'outcome_simulation_results', 'outcome_guardrail_drafts',
                                     'outcome_domain_events')
                """, Integer.class);
        assertThat(tableCount).isEqualTo(5);

        String scenarioId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO outcome_scenarios (
                    scenario_id, user_id, name, status, current_version, currency, time_zone,
                    create_idempotency_key, create_request_fingerprint, created_at, updated_at, version)
                VALUES (?, 'smoke-owner', 'Rent buffer', 'ACTIVE', 1, 'USD', 'UTC',
                        ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, scenarioId, "create-" + scenarioId, "fingerprint-" + scenarioId);
        String versionId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO outcome_scenario_versions (
                    version_id, scenario_id, scenario_version, horizon_start, horizon_days,
                    protected_minimum, account_ids_json, assumptions_json, shocks_json,
                    ledger_snapshot_json, schedule_snapshot_json, source_fingerprint,
                    mutation_idempotency_key, request_fingerprint, created_at)
                VALUES (?, ?, 1, CURRENT_DATE, 30, 10000.00, '[]', '[]', '[]', '[]', '[]',
                        ?, ?, ?, CURRENT_TIMESTAMP)
                """, versionId, scenarioId, "source-" + scenarioId, "mutation-" + scenarioId,
                "request-" + scenarioId);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE outcome_scenario_versions SET horizon_days = 31 WHERE version_id = ?", versionId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
    }
    @Test
    void addsOutcomeTypeReplayCertificateAndDraftSelectionEvidence() {
        Integer columnCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND ((table_name = 'outcome_scenario_versions' AND column_name IN
                        ('outcome_type', 'protected_obligation_json', 'canonical_inputs_json'))
                    OR (table_name = 'outcome_simulation_results' AND column_name IN
                        ('engine_version', 'candidate_actions_json', 'replay_output_json',
                         'certificate_hash', 'ranking_factors_json', 'rejection_reasons_json',
                         'repair_evaluated_combinations', 'repair_search_capped'))
                    OR (table_name = 'outcome_guardrail_drafts' AND column_name IN
                        ('alternative_rank', 'candidate_actions_json', 'replay_proof_json',
                         'replay_certificate_hash', 'preview_selected_at',
                         'preview_selection_idempotency_key')))
                """, Integer.class);
        assertThat(columnCount).isEqualTo(17);
    }

    @Test
    void addsVersionedCanonicalSourceComponentsForFreshnessChecks() {
        Integer columnCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'outcome_scenario_versions'
                  AND column_name IN ('source_fingerprint_schema', 'source_components_json')
                """, Integer.class);
        assertThat(columnCount).isEqualTo(2);

        String legacyDefault = jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'outcome_scenario_versions'
                  AND column_name = 'source_fingerprint_schema'
                """, String.class);
        assertThat(legacyDefault).contains("outcome-source-v1");
    }
    @Test
    void createsFailClosedConsentGuardrailControlAndImmutableEvidence() {
        Integer tableCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('outcome_guardrail_policies', 'outcome_guardrail_executions',
                                     'outcome_guardrail_runtime_controls', 'outcome_guardrail_control_events')
                """, Integer.class);
        assertThat(tableCount).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT execution_enabled FROM outcome_guardrail_runtime_controls
                WHERE control_id = 'GLOBAL'
                """, Boolean.class)).isFalse();

        String eventId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO outcome_guardrail_control_events (
                    event_id, execution_enabled, reason, actor, idempotency_key,
                    request_fingerprint, created_at)
                VALUES (?, FALSE, 'fresh smoke evidence', 'operator', ?, ?, CURRENT_TIMESTAMP)
                """, eventId, "control-" + eventId, "fingerprint-" + eventId);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE outcome_guardrail_control_events SET reason = 'changed' WHERE event_id = ?", eventId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
    }
}
