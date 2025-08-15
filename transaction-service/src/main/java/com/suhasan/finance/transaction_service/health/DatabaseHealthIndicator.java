package com.suhasan.finance.transaction_service.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Health indicator for PostgreSQL database connectivity
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    
    @Override
    public Health health() {
        try {
            return checkDatabaseHealth();
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("database", "PostgreSQL");
        }
    }
    
    private Health checkDatabaseHealth() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            // Test basic connectivity
            if (!connection.isValid(5)) {
                return Health.down()
                        .withDetail("error", "Database connection is not valid")
                        .withDetail("database", "PostgreSQL");
            }
            
            // Test query execution
            try (PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt(1) == 1) {
                        // Get additional database info
                        String databaseUrl = connection.getMetaData().getURL();
                        String databaseVersion = connection.getMetaData().getDatabaseProductVersion();
                        
                        return Health.up()
                                .withDetail("database", "PostgreSQL")
                                .withDetail("url", maskPassword(databaseUrl))
                                .withDetail("version", databaseVersion)
                                .withDetail("validationQuery", "SELECT 1");
                    }
                }
            }
            
            return Health.down()
                    .withDetail("error", "Database query validation failed")
                    .withDetail("database", "PostgreSQL");
        }
    }
    
    private String maskPassword(String url) {
        if (url == null) return null;
        // Mask password in connection URL for security
        return url.replaceAll("password=[^&;]*", "password=***");
    }
}