package com.suhasan.finance.account_service.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerProjectionMirrorMigrationTest {

    @Test
    void migrationBackfillsCurrencyAndProjectionMirrorDefaults() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ledger-projection-migration-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE accounts (
                    id BIGINT PRIMARY KEY,
                    balance NUMERIC(38,2) NOT NULL,
                    ledger_balance NUMERIC(38,2) NOT NULL,
                    available_balance NUMERIC(38,2) NOT NULL
                )
                """);
        jdbc.update("""
                INSERT INTO accounts (id, balance, ledger_balance, available_balance)
                VALUES (1, 123.45, 123.45, 123.45)
                """);

        FileSystemResource migration = new FileSystemResource(
                "src/main/resources/db/migration/V6__add_currency_and_ledger_projection_mirror.sql");
        assertThat(migration.exists()).as("V6 ledger projection migration exists").isTrue();
        String migrationSql = Files.readString(migration.getFile().toPath());
        assertThat(migrationSql).contains("prevent_account_currency_change");
        String portableSql = migrationSql.split("-- PostgreSQL-only currency immutability trigger", 2)[0];
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ByteArrayResource(portableSql.getBytes(StandardCharsets.UTF_8)));
        populator.execute(dataSource);

        MirrorRow row = jdbc.queryForObject("""
                        SELECT currency, pending_balance, ledger_projection_version
                        FROM accounts WHERE id = 1
                        """,
                (resultSet, rowNumber) -> new MirrorRow(
                        resultSet.getString("currency"),
                        resultSet.getBigDecimal("pending_balance"),
                        resultSet.getLong("ledger_projection_version")));

        assertThat(row).isNotNull();
        assertThat(row.currency()).isEqualTo("USD");
        assertThat(row.pendingBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(row.ledgerProjectionVersion()).isZero();
    }

    private record MirrorRow(String currency, BigDecimal pendingBalance, long ledgerProjectionVersion) {
    }
}
