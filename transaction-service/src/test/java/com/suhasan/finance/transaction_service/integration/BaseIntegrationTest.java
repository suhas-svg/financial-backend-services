package com.suhasan.finance.transaction_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.embedded.RedisServer;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * Base class for integration tests using Testcontainers for PostgreSQL and embedded Redis.
 * Provides WireMock server for Account Service integration testing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureWebMvc
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "logging.level.org.springframework.web=DEBUG",
    "logging.level.com.suhasan.finance.transaction_service=DEBUG"
})
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
            .withPassword("test")
            .withInitScript("test-init.sql");

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
        redisTemplate.getConnectionFactory().getConnection().flushAll();
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