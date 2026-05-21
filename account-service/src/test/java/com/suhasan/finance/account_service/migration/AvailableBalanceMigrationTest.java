package com.suhasan.finance.account_service.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AvailableBalanceMigrationTest {

    @Test
    void v4BackfillsLedgerAndAvailableBalancesFromExistingBalance() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:available-balance-migration-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE accounts (id BIGINT PRIMARY KEY, balance NUMERIC(38,2) NOT NULL)");
        jdbc.update("INSERT INTO accounts (id, balance) VALUES (?, ?)", 1L, new BigDecimal("123.45"));

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new FileSystemResource("src/main/resources/db/migration/V4__add_available_balance_and_debit_holds.sql"));
        populator.execute(dataSource);

        BigDecimal ledgerBalance = jdbc.queryForObject(
                "SELECT ledger_balance FROM accounts WHERE id = 1",
                BigDecimal.class);
        BigDecimal availableBalance = jdbc.queryForObject(
                "SELECT available_balance FROM accounts WHERE id = 1",
                BigDecimal.class);
        Integer holdTableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'ACCOUNT_DEBIT_HOLDS'",
                Integer.class);

        assertThat(ledgerBalance).isEqualByComparingTo("123.45");
        assertThat(availableBalance).isEqualByComparingTo("123.45");
        assertThat(holdTableCount).isEqualTo(1);
    }
}
