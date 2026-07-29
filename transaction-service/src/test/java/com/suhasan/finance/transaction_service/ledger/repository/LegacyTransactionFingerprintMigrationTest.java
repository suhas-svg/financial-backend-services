package com.suhasan.finance.transaction_service.ledger.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Execution(ExecutionMode.SAME_THREAD)
@Testcontainers(disabledWithoutDocker = true)
class LegacyTransactionFingerprintMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("legacy_fingerprint_migration")
            .withUsername("test")
            .withPassword("test");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateLegacyRows() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("100"))
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

        insertLegacy("legacy-deposit", "DEPOSIT", "EXTERNAL", "account-1",
                "25.00", "legacy deposit", "deposit-ref", "deposit-key");
        insertLegacy("legacy-transfer", "TRANSFER", "account-1", "account-2",
                "30.00", "legacy transfer", "transfer-ref", "transfer-key");
        insertLegacy("legacy-unkeyed", "DEPOSIT", "EXTERNAL", "account-1",
                "10.00", "unkeyed", "unkeyed-ref", null);

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void reconstructsCanonicalDepositAndQuarantinesAmbiguousTransfer() throws Exception {
        assertThat(jdbc.queryForObject("""
                SELECT request_fingerprint_status
                FROM transactions WHERE transaction_id = 'legacy-deposit'
                """, String.class)).isEqualTo("LEGACY_RECONSTRUCTED");
        assertThat(jdbc.queryForObject("""
                SELECT request_fingerprint
                FROM transactions WHERE transaction_id = 'legacy-deposit'
                """, String.class)).isEqualTo(sha256(
                "DEPOSIT|migration-owner|account-1|25|legacy deposit|deposit-ref"));

        assertThat(jdbc.queryForObject("""
                SELECT request_fingerprint_status
                FROM transactions WHERE transaction_id = 'legacy-transfer'
                """, String.class)).isEqualTo("LEGACY_AMBIGUOUS");
        assertThat(jdbc.queryForObject("""
                SELECT length(request_fingerprint)
                FROM transactions WHERE transaction_id = 'legacy-transfer'
                """, Integer.class)).isEqualTo(64);

        assertThat(jdbc.queryForObject("""
                SELECT request_fingerprint_status
                FROM transactions WHERE transaction_id = 'legacy-unkeyed'
                """, String.class)).isEqualTo("NOT_APPLICABLE");
        assertThat(jdbc.queryForObject("""
                SELECT request_fingerprint IS NULL
                FROM transactions WHERE transaction_id = 'legacy-unkeyed'
                """, Boolean.class)).isTrue();
    }

    @Test
    void keyedRowsCannotBeInsertedWithoutFingerprintEvidence() {
        assertThatThrownBy(() -> insertLegacy(
                UUID.randomUUID().toString(), "DEPOSIT", "EXTERNAL", "account-1",
                "5.00", "invalid", "invalid-ref", "missing-fingerprint"))
                .isInstanceOf(Exception.class);
    }

    private static void insertLegacy(
            String transactionId,
            String type,
            String fromAccountId,
            String toAccountId,
            String amount,
            String description,
            String reference,
            String idempotencyKey) {
        jdbc.update("""
                INSERT INTO transactions (
                    transaction_id, from_account_id, to_account_id, amount, currency,
                    type, status, processing_state, description, reference,
                    idempotency_key, created_at, created_by)
                VALUES (?, ?, ?, ?::numeric, 'USD', ?, 'COMPLETED', 'COMPLETED', ?, ?, ?,
                        CURRENT_TIMESTAMP, 'migration-owner')
                """, transactionId, fromAccountId, toAccountId, amount, type,
                description, reference, idempotencyKey);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
