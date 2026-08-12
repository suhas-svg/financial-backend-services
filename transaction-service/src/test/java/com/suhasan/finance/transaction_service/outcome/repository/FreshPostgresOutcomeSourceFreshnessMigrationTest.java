package com.suhasan.finance.transaction_service.outcome.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class FreshPostgresOutcomeSourceFreshnessMigrationTest {
    @Test
    void migratesVersionedSourceFingerprintEvidenceOnFreshPostgres() throws Exception {
        String url = System.getenv("FRESH_TRANSACTION_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        String user = System.getenv().getOrDefault("FRESH_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("FRESH_POSTGRES_PASSWORD", "postgres");

        Flyway flyway = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration").cleanDisabled(true).load();
        assertThat(flyway.migrate().success).isTrue();

        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT column_name, column_default, is_nullable
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'outcome_scenario_versions'
                       AND column_name IN ('source_fingerprint_schema', 'source_components_json')
                     ORDER BY column_name
                     """)) {
            int count = 0;
            while (rows.next()) {
                count++;
                assertThat(rows.getString("is_nullable")).isEqualTo("NO");
                assertThat(rows.getString("column_default")).isNotBlank();
            }
            assertThat(count).isEqualTo(2);
        }
    }
}
