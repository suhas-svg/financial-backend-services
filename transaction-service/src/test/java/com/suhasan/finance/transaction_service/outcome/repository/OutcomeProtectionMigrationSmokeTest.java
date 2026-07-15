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
}
