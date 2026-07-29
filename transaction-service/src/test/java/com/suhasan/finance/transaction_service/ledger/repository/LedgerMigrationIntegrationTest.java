package com.suhasan.finance.transaction_service.ledger.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Execution(ExecutionMode.SAME_THREAD)
@Testcontainers(disabledWithoutDocker = true)
class LedgerMigrationIntegrationTest {

    private static JdbcTemplate jdbc;
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("ledger_migrations")
            .withUsername("test")
            .withPassword("test");
    private static String jdbcUrl;
    private static String username;
    private static String password;

    @BeforeAll
    static void migrate() {
        jdbcUrl = System.getenv("LEDGER_TEST_JDBC_URL");
        username = System.getenv().getOrDefault("LEDGER_TEST_DB_USER", "test");
        password = System.getenv().getOrDefault("LEDGER_TEST_DB_PASSWORD", "test");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbcUrl = postgres.getJdbcUrl();
            username = postgres.getUsername();
            password = postgres.getPassword();
        }
        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                jdbcUrl, username, password));
    }

    @AfterAll
    static void stopContainer() {
        // The Testcontainers JUnit extension owns container lifecycle.
    }

    @Test
    void spendingReservationClaimConstraintAcceptsRecoverableCompletedState() {
        String claimId = UUID.randomUUID().toString();
        String idempotencyKey = "migration-" + claimId;
        jdbc.update("""
                INSERT INTO transaction_idempotency_claims (
                    claim_id, user_id, transaction_type, idempotency_key, request_fingerprint,
                    account_id, operation_type, amount, currency, state,
                    created_at, updated_at, expires_at)
                VALUES (?, 'migration-owner', 'WITHDRAWAL', ?, ?, '7', 'WITHDRAWAL',
                        25.00, 'USD', 'COMPLETED_PENDING_CONSUME',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '30 minutes')
                """, claimId, idempotencyKey, "a".repeat(64));

        assertThat(jdbc.queryForObject(
                "SELECT state FROM transaction_idempotency_claims WHERE claim_id = ?",
                String.class, claimId)).isEqualTo("COMPLETED_PENDING_CONSUME");

        jdbc.update("UPDATE transaction_idempotency_claims SET state = 'COMPLETED' WHERE claim_id = ?",
                claimId);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM transaction_idempotency_claims WHERE claim_id = ?",
                String.class, claimId)).isEqualTo("COMPLETED");
    }

    @Test
    void migrationsCreateLedgerTablesAndAcceptEveryProcessingState() {
        Integer tableCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('ledger_accounts', 'journal_transactions', 'journal_postings',
                                     'journal_state_events', 'ledger_balance_projections',
                                     'ledger_projection_outbox', 'financial_evidence_outbox',
                                     'financial_evidence_deliveries')
                """, Integer.class);
        assertThat(tableCount).isEqualTo(8);

        String transactionId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO transactions (
                    transaction_id, from_account_id, to_account_id, amount, currency,
                    type, status, processing_state, created_at, created_by)
                VALUES (?, '1', '2', 10.00, 'USD', 'TRANSFER', 'PROCESSING',
                        'INITIATED', CURRENT_TIMESTAMP, 'migration-test')
                """, transactionId);

        for (String state : new String[]{
                "HOLD_PLACED", "HOLD_CAPTURED", "HOLD_RELEASED", "DEBIT_APPLIED",
                "CREDIT_APPLIED", "COMPLETED", "COMPENSATED", "MANUAL_ACTION_REQUIRED"}) {
            jdbc.update("UPDATE transactions SET processing_state = ? WHERE transaction_id = ?", state, transactionId);
        }
    }

    @Test
    void deferredConstraintAcceptsBalancedJournalAndRejectsUnbalancedJournal() throws Exception {
        UUID customer = insertLedgerAccount("CUSTOMER", "USD", UUID.randomUUID().toString());
        UUID clearing = insertLedgerAccount("CLEARING", "USD", null);

        UUID balanced = insertJournal(customer, clearing, "USD", "USD",
                new BigDecimal("10.00"), new BigDecimal("10.00"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_postings WHERE journal_id = ?", Integer.class, balanced))
                .isEqualTo(2);

        assertThatThrownBy(() -> insertJournal(customer, clearing, "USD", "USD",
                new BigDecimal("10.00"), new BigDecimal("9.00")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("balance");
    }

    @Test
    void deferredConstraintRejectsPostingCurrencyMismatch() {
        UUID customer = insertLedgerAccount("CUSTOMER", "USD", UUID.randomUUID().toString());
        UUID clearing = insertLedgerAccount("CLEARING", "USD", null);

        assertThatThrownBy(() -> insertJournal(customer, clearing, "USD", "EUR",
                new BigDecimal("10.00"), new BigDecimal("10.00")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void postedLedgerRowsCannotBeUpdatedOrDeleted() throws Exception {
        UUID customer = insertLedgerAccount("CUSTOMER", "USD", UUID.randomUUID().toString());
        UUID clearing = insertLedgerAccount("CLEARING", "USD", null);
        UUID journalId = insertJournal(customer, clearing, "USD", "USD",
                new BigDecimal("10.00"), new BigDecimal("10.00"));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE journal_transactions SET description = 'changed' WHERE journal_id = ?", journalId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM journal_postings WHERE journal_id = ?", journalId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void outcomeProtectionMigrationCreatesImmutableVersionedProofTables() {
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
                VALUES (?, 'migration-owner', 'Rent buffer', 'ACTIVE', 1, 'USD', 'UTC',
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
    void productionReadinessMigrationCreatesOutboxTimezoneAndOperatorEvidence() {
        Integer deliveryTable = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'outcome_notification_deliveries'
                """, Integer.class);
        assertThat(deliveryTable).isEqualTo(1);

        Integer scheduleColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'scheduled_transfers'
                  AND column_name IN ('source_time_zone', 'source_local_date_time',
                                      'dst_overlap_policy', 'dst_gap_policy',
                                      'recurrence_anchor_day', 'recurrence_anchor_end_of_month')
                """, Integer.class);
        assertThat(scheduleColumns).isEqualTo(6);

        Integer bootstrapColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'ledger_bootstrap_runs'
                  AND column_name IN ('requested_role', 'request_id')
                """, Integer.class);
        assertThat(bootstrapColumns).isEqualTo(2);
    }

    @Test
    void consentGuardrailMigrationFailsClosedAndPreservesControlEvidence() {
        Integer tableCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('outcome_guardrail_policies', 'outcome_guardrail_executions',
                                     'outcome_guardrail_runtime_controls', 'outcome_guardrail_control_events')
                """, Integer.class);
        assertThat(tableCount).isEqualTo(4);

        Boolean enabled = jdbc.queryForObject("""
                SELECT execution_enabled FROM outcome_guardrail_runtime_controls
                WHERE control_id = 'GLOBAL'
                """, Boolean.class);
        assertThat(enabled).isFalse();

        String eventId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO outcome_guardrail_control_events (
                    event_id, execution_enabled, reason, actor, idempotency_key,
                    request_fingerprint, created_at)
                VALUES (?, FALSE, 'migration evidence', 'operator', ?, ?, CURRENT_TIMESTAMP)
                """, eventId, "control-" + eventId, "fingerprint-" + eventId);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE outcome_guardrail_control_events SET reason = 'changed' WHERE event_id = ?", eventId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
    }
    @Test
    void freshMigrationSeedsRequiredZeroBalanceSystemAccounts() {
        Integer accountCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ledger_accounts
                WHERE account_kind IN ('CLEARING', 'SUSPENSE', 'FEE')
                  AND currency IN ('USD', 'EUR', 'GBP', 'INR')
                  AND status = 'ACTIVE'
                """, Integer.class);
        assertThat(accountCount).isEqualTo(12);

        Integer projectionCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ledger_accounts account
                JOIN ledger_balance_projections projection
                  ON projection.ledger_account_id = account.ledger_account_id
                WHERE account.account_kind IN ('CLEARING', 'SUSPENSE', 'FEE')
                  AND account.currency IN ('USD', 'EUR', 'GBP', 'INR')
                  AND projection.posted_balance = 0
                  AND projection.pending_balance = 0
                  AND projection.available_balance = 0
                """, Integer.class);
        assertThat(projectionCount).isEqualTo(12);
    }

    private static UUID insertLedgerAccount(String kind, String currency, String externalAccountId) {
        if (externalAccountId == null && !"CUSTOMER".equals(kind)) {
            var existing = jdbc.query(
                    "SELECT ledger_account_id FROM ledger_accounts WHERE account_kind = ? AND currency = ? AND status = 'ACTIVE'",
                    (resultSet, rowNumber) -> resultSet.getObject("ledger_account_id", UUID.class),
                    kind, currency);
            if (!existing.isEmpty()) {
                return existing.getFirst();
            }
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ledger_accounts (
                    ledger_account_id, account_kind, currency, external_account_id,
                    owner_id, status, created_at, version)
                VALUES (?, ?, ?, ?, 'migration-owner', 'ACTIVE', CURRENT_TIMESTAMP, 0)
                """, id, kind, currency, externalAccountId);
        return id;
    }

    private static UUID insertJournal(
            UUID debitAccount,
            UUID creditAccount,
            String journalCurrency,
            String creditCurrency,
            BigDecimal debit,
            BigDecimal credit) throws SQLException {
        UUID journalId = UUID.randomUUID();
        try (Connection connection = java.sql.DriverManager.getConnection(
                jdbcUrl, username, password)) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO journal_transactions (
                            journal_id, journal_reference, journal_type, currency, effective_date,
                            description, correlation_id, created_by, created_at,
                            idempotency_scope, idempotency_key, request_fingerprint)
                        VALUES (?, ?, 'TRANSFER', ?, ?, 'migration test', ?, 'migration-test',
                                CURRENT_TIMESTAMP, 'migration-test', ?, ?)
                        """)) {
                    statement.setObject(1, journalId);
                    statement.setString(2, "JRN-" + journalId);
                    statement.setString(3, journalCurrency);
                    statement.setObject(4, LocalDate.now());
                    statement.setString(5, journalId.toString());
                    statement.setString(6, journalId.toString());
                    statement.setString(7, "fingerprint-" + journalId);
                    statement.executeUpdate();
                }
                insertPosting(connection, journalId, debitAccount, 1, "DEBIT", debit, journalCurrency);
                insertPosting(connection, journalId, creditAccount, 2, "CREDIT", credit, creditCurrency);
                insertState(connection, journalId, "PENDING");
                connection.commit();
                return journalId;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void insertPosting(
            Connection connection, UUID journalId, UUID accountId, int sequence,
            String direction, BigDecimal amount, String currency) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO journal_postings (
                    posting_id, journal_id, ledger_account_id, posting_sequence,
                    direction, amount, currency, memo)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'migration test')
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, journalId);
            statement.setObject(3, accountId);
            statement.setInt(4, sequence);
            statement.setString(5, direction);
            statement.setBigDecimal(6, amount);
            statement.setString(7, currency);
            statement.executeUpdate();
        }
    }

    private static void insertState(Connection connection, UUID journalId, String state) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO journal_state_events (
                    event_id, journal_id, event_sequence, state, actor, reason, created_at)
                VALUES (?, ?, 1, ?, 'migration-test', 'test', CURRENT_TIMESTAMP)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, journalId);
            statement.setString(3, state);
            statement.executeUpdate();
        }
    }
}
