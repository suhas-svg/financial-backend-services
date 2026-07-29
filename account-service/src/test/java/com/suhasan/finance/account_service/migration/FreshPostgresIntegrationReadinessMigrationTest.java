package com.suhasan.finance.account_service.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class FreshPostgresIntegrationReadinessMigrationTest {
    @Test
    void migratesProviderReceiptAndMfaKeyEvidenceOnFreshPostgres() throws Exception {
        String url = System.getenv("FRESH_ACCOUNT_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        String user = System.getenv().getOrDefault("FRESH_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("FRESH_POSTGRES_PASSWORD", "postgres");

        Flyway flyway = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration").cleanDisabled(true).load();
        assertThat(flyway.migrate().success).isTrue();

        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            try (var columns = statement.executeQuery("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'user_mfa_methods'
                      AND column_name = 'secret_key_id'
                    """)) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getInt(1)).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name IN ('notification_provider_receipts','mfa_key_rotation_runs')
                    """)) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }
}
