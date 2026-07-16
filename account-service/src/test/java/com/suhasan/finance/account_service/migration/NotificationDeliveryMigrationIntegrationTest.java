package com.suhasan.finance.account_service.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class NotificationDeliveryMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("account_notification_migrations")
            .withUsername("test")
            .withPassword("test");

    @Test
    void freshPostgresHasDurableNotificationReceiptConstraints() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            try (ResultSet columns = statement.executeQuery("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'notifications'
                      AND column_name IN ('delivery_id', 'first_received_at',
                                          'last_received_at', 'delivery_count')
                    """)) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getInt(1)).isEqualTo(4);
            }
            try (ResultSet index = statement.executeQuery("""
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND indexname = 'uq_notifications_delivery_id'
                    """)) {
                assertThat(index.next()).isTrue();
                assertThat(index.getInt(1)).isEqualTo(1);
            }
        }
    }
}
