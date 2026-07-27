package com.suhasan.finance.account_service.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpendingLimitReservationMigrationTest {
    @Test
    void upgradesLegacyReservationsWithoutCollapsingAmbiguousKeys() throws Exception {
        String url = System.getenv("FRESH_ACCOUNT_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        String user = System.getenv().getOrDefault("FRESH_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("FRESH_POSTGRES_PASSWORD", "postgres");
        String schema = "reservation_upgrade_" + UUID.randomUUID().toString().replace("-", "");

        try {
            Flyway legacy = Flyway.configure()
                    .dataSource(url, user, password)
                    .locations("classpath:db/migration")
                    .schemas(schema)
                    .defaultSchema(schema)
                    .target("9")
                    .cleanDisabled(true)
                    .load();
            assertThat(legacy.migrate().success).isTrue();

            try (var connection = DriverManager.getConnection(url, user, password);
                 var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO %s.accounts (
                            id, version, owner_id, balance, ledger_balance, available_balance,
                            currency, pending_balance, ledger_projection_version,
                            created_at, account_type, status
                        ) VALUES (
                            1, 0, 'alice', 1000.00, 1000.00, 1000.00,
                            'USD', 0.00, 0, CURRENT_DATE, 'CHECKING', 'ACTIVE'
                        )
                        """.formatted(schema));
                statement.executeUpdate("""
                        INSERT INTO %s.spending_limit_reservations
                            (account_id, operation_type, amount, usage_date, idempotency_key, created_at)
                        VALUES
                            (1, 'TRANSFER', 10.00, CURRENT_DATE, 'ambiguous-key', CURRENT_TIMESTAMP),
                            (1, 'WITHDRAWAL', 10.00, CURRENT_DATE, 'ambiguous-key', CURRENT_TIMESTAMP),
                            (1, 'TRANSFER', 5.00, CURRENT_DATE, 'unambiguous-key', CURRENT_TIMESTAMP)
                        """.formatted(schema));
            }

            Flyway upgraded = Flyway.configure()
                    .dataSource(url, user, password)
                    .locations("classpath:db/migration")
                    .schemas(schema)
                    .defaultSchema(schema)
                    .cleanDisabled(true)
                    .load();
            assertThat(upgraded.migrate().success).isTrue();

            try (var connection = DriverManager.getConnection(url, user, password);
                 var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("""
                        SELECT request_scope, state, outcome
                        FROM %s.spending_limit_reservations
                        WHERE idempotency_key = 'ambiguous-key'
                        ORDER BY reservation_id
                        """.formatted(schema))) {
                    int count = 0;
                    while (rows.next()) {
                        count++;
                        assertThat(rows.getString("request_scope")).isNull();
                        assertThat(rows.getString("state")).isEqualTo("RECONCILIATION_REQUIRED");
                        assertThat(rows.getString("outcome"))
                                .isEqualTo("AMBIGUOUS_LEGACY_IDEMPOTENCY_SCOPE");
                    }
                    assertThat(count).isEqualTo(2);
                }

                try (var rows = statement.executeQuery("""
                        SELECT owner_id, currency, request_scope, state, updated_at, expires_at
                        FROM %s.spending_limit_reservations
                        WHERE idempotency_key = 'unambiguous-key'
                        """.formatted(schema))) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("owner_id")).isEqualTo("alice");
                    assertThat(rows.getString("currency")).isEqualTo("USD");
                    assertThat(rows.getString("request_scope")).isEqualTo("1|unambiguous-key");
                    assertThat(rows.getString("state")).isEqualTo("RESERVED");
                    assertThat(rows.getTimestamp("updated_at")).isNotNull();
                    assertThat(rows.getTimestamp("expires_at")).isNotNull();
                }
            }
        } finally {
            try (var connection = DriverManager.getConnection(url, user, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
