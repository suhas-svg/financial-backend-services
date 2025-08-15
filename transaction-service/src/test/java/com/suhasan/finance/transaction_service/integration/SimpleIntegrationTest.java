package com.suhasan.finance.transaction_service.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple integration test to verify Testcontainers setup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SimpleIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    // PostgreSQL Testcontainer
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("transactiondb_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-init.sql");

    // Redis Testcontainer
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // Redis configuration
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        
        // Account Service configuration (mock URL)
        registry.add("account-service.base-url", () -> "http://localhost:9999");
    }

    @Test
    void shouldStartApplicationWithTestcontainers() {
        // Given - Application should start successfully with Testcontainers
        
        // When - Make request to actuator health endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                String.class
        );

        // Then - Should return health status
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void shouldConnectToPostgreSQLContainer() {
        // Given - PostgreSQL container should be running
        assertThat(postgres.isRunning()).isTrue();
        
        // When - Check JDBC URL
        String jdbcUrl = postgres.getJdbcUrl();
        
        // Then - Should have valid connection details
        assertThat(jdbcUrl).contains("postgresql");
        assertThat(jdbcUrl).contains("transactiondb_test");
    }

    @Test
    void shouldConnectToRedisContainer() {
        // Given - Redis container should be running
        assertThat(redis.isRunning()).isTrue();
        
        // When - Check exposed port
        Integer redisPort = redis.getMappedPort(6379);
        
        // Then - Should have valid port mapping
        assertThat(redisPort).isGreaterThan(0);
    }

    @Test
    void shouldRejectUnauthenticatedRequests() {
        // Given - No authentication token
        
        // When - Make request to protected endpoint
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/transactions/transfer",
                null,
                String.class
        );

        // Then - Should return unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}