package com.suhasan.finance.account_service.migration;

import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeneficiaryMigrationTest {

    @Test
    void v7CreatesBeneficiariesTableAndCheckConstraints() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:beneficiary-migration-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        FileSystemResource migration = new FileSystemResource(
                "src/main/resources/db/migration/V7__create_beneficiaries.sql");
        assertThat(migration.exists()).as("V7 beneficiaries migration exists").isTrue();
        String migrationSql = Files.readString(migration.getFile().toPath());
        assertThat(migrationSql)
                .as("PostgreSQL keeps only one active saved recipient for a destination")
                .contains("CREATE UNIQUE INDEX uq_beneficiaries_active_destination")
                .contains("WHERE status = 'ACTIVE'");

        String portableSql = migrationSql.split("CREATE UNIQUE INDEX uq_beneficiaries_active_destination", 2)[0];
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ByteArrayResource(portableSql.getBytes(StandardCharsets.UTF_8)));
        populator.execute(dataSource);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer tableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'BENEFICIARIES'",
                Integer.class);
        Integer userStatusIndexCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME = 'IDX_BENEFICIARIES_USER_STATUS'",
                Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(userStatusIndexCount).isEqualTo(1);

        insertBeneficiary(jdbc, "beneficiary-1", "customer-a", "External Checking", "200", "USD", "ACTIVE");

        assertThatThrownBy(() -> insertBeneficiary(
                jdbc, "beneficiary-4", "customer-a", "Unsupported Currency", "201", "JPY", "ACTIVE"))
                .hasRootCauseInstanceOf(JdbcSQLIntegrityConstraintViolationException.class);
        assertThatThrownBy(() -> insertBeneficiary(
                jdbc, "beneficiary-5", "customer-a", "Unsupported Status", "202", "USD", "PENDING"))
                .hasRootCauseInstanceOf(JdbcSQLIntegrityConstraintViolationException.class);
    }

    private void insertBeneficiary(
            JdbcTemplate jdbc,
            String beneficiaryId,
            String userId,
            String displayName,
            String destinationAccountId,
            String currency,
            String status) {
        Timestamp now = Timestamp.from(Instant.parse("2026-07-02T00:00:00Z"));
        jdbc.update("""
                        INSERT INTO beneficiaries (
                            beneficiary_id,
                            user_id,
                            display_name,
                            destination_account_id,
                            currency,
                            status,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                beneficiaryId,
                userId,
                displayName,
                destinationAccountId,
                currency,
                status,
                now,
                now);
    }
}
