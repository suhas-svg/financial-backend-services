package com.suhasan.finance.transaction_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

/**
 * Base class for integration tests using Testcontainers for PostgreSQL and
 * embedded Redis.
 * Provides WireMock server for Account Service integration testing.
 */
@SpringBootTest(classes = com.suhasan.finance.transaction_service.TransactionServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureWebMvc
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.com.suhasan.finance.transaction_service=DEBUG"
})
@SuppressWarnings({ "resource", "null" })
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected RedisTemplate<String, Object> redisTemplate;

    // PostgreSQL Testcontainer
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("transactiondb_test")
            .withUsername("test")
            .withPassword("test");

    // Redis Testcontainer
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // WireMock server for Account Service
    protected static WireMockServer wireMockServer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis configuration
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // Account Service configuration (WireMock)
        registry.add("account-service.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @BeforeAll
    static void beforeAll() {
        // Start WireMock server
        wireMockServer = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort()
                .usingFilesUnderClasspath("wiremock"));
        wireMockServer.start();
    }

    @AfterAll
    static void afterAll() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // Reset WireMock server before each test
        wireMockServer.resetAll();

        // Clear Redis cache before each test
        clearRedisCache();
    }

    private void clearRedisCache() {
        if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
            log.debug("Skipping Redis cleanup because connection factory is unavailable");
            return;
        }

        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        } catch (Exception e) {
            // Do not fail the entire test setup because Redis is unavailable or slow.
            log.warn("Unable to clear Redis cache before test setup: {}", e.getMessage());
        }
    }

    /**
     * Get the base URL for the application
     */
    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Get the WireMock server instance for stubbing Account Service responses
     */
    protected WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
