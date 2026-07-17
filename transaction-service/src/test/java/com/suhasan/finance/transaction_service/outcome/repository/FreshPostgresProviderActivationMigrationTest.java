package com.suhasan.finance.transaction_service.outcome.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class FreshPostgresProviderActivationMigrationTest {
    @Test
    void migratesProviderActivationControlPlaneOnFreshPostgres() throws Exception {
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
                     SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_name IN ('provider_activations',
                                          'provider_activation_events',
                                          'provider_certification_runs',
                                          'provider_webhook_replay_evidence')
                     """)) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(4);
        }
    }
}
