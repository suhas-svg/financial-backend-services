package com.suhasan.finance.transaction_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.suhasan.finance.transaction_service.service.SpendingLimitReservationLifecycleClient;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

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
        "account-service.timeout=1000",
        "account-service.resilience.time-limiter.timeout=1000",
        "account-service.resilience.retry.max-attempts=1",
        "account-service.resilience.circuit-breaker.minimum-number-of-calls=100",
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.com.suhasan.finance.transaction_service=DEBUG"
})
@SuppressWarnings({ "resource", "null" })
public abstract class BaseIntegrationTest {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final ClientHttpRequestInterceptor IDEMPOTENCY_KEY_INTERCEPTOR = (request, body, execution) -> {
        final String path = request.getURI().getPath();
        final boolean requiresIdempotencyKey = HttpMethod.POST.equals(request.getMethod())
                && ("/api/transactions/transfer".equals(path)
                || "/api/transactions/withdraw".equals(path)
                || path.matches("/api/transactions/[^/]+/reverse"));
        if (requiresIdempotencyKey && !request.getHeaders().containsKey(IDEMPOTENCY_KEY_HEADER)) {
            request.getHeaders().set(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
        }
        return execution.execute(request, body);
    };

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

    @MockitoBean
    protected SpendingLimitReservationLifecycleClient spendingLimitReservationLifecycleClient;

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
        // Apache HttpClient automatically retries POST responses such as 503. That
        // hides the first server response and can turn an idempotent replay into a
        // misleading success. Use the JDK request factory so each integration-test
        // request is executed exactly once.
        if (!(restTemplate.getRestTemplate().getRequestFactory() instanceof SimpleClientHttpRequestFactory)) {
            restTemplate.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory());
        }

        if (!restTemplate.getRestTemplate().getInterceptors().contains(IDEMPOTENCY_KEY_INTERCEPTOR)) {
            restTemplate.getRestTemplate().getInterceptors().add(IDEMPOTENCY_KEY_INTERCEPTOR);
        }

        stubSpendingLimitReservationLifecycle();

        // Reset WireMock server before each test
        wireMockServer.resetAll();

        // Clear Redis cache before each test
        clearRedisCache();
    }

    private void stubSpendingLimitReservationLifecycle() {
        when(spendingLimitReservationLifecycleClient.reserve(
                anyString(), anyString(), any(BigDecimal.class), anyString(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenAnswer(invocation -> reservationResponse(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6)));

        when(spendingLimitReservationLifecycleClient.transition(
                anyString(), anyLong(), anyString(), anyString(),
                nullable(String.class), nullable(String.class)))
                .thenAnswer(invocation -> transitionResponse(
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(4),
                        invocation.getArgument(5)));
    }

    private SpendingLimitReservationLifecycleClient.ReservationResponse reservationResponse(
            String accountId, String operationType, BigDecimal amount, String idempotencyKey,
            String userId, String currency, String transactionCorrelation) {
        String normalizedCurrency = normalizeCurrency(currency);
        String normalizedOperation = operationType.trim().toUpperCase(Locale.ROOT);
        String normalizedKey = idempotencyKey.trim();
        long reservationId = Integer.toUnsignedLong(normalizedKey.hashCode());
        if (reservationId == 0L) {
            reservationId = 1L;
        }
        LocalDateTime now = LocalDateTime.now();
        BigDecimal dailyLimit = amount.add(new BigDecimal("10000.00"));
        return new SpendingLimitReservationLifecycleClient.ReservationResponse(
                true,
                false,
                normalizedCurrency,
                dailyLimit,
                BigDecimal.ZERO,
                dailyLimit.subtract(amount),
                null,
                reservationId,
                transactionCorrelation,
                amount,
                reservationFingerprint(accountId, userId, normalizedOperation,
                        amount, normalizedCurrency, normalizedKey),
                "RESERVED",
                now,
                now,
                now.plusMinutes(30),
                null);
    }

    private SpendingLimitReservationLifecycleClient.ReservationResponse transitionResponse(
            Long reservationId, String action, String transactionCorrelation, String outcome) {
        String state = switch (action.trim().toLowerCase(Locale.ROOT)) {
            case "consume" -> "CONSUMED";
            case "release" -> "RELEASED";
            case "reconciliation-required" -> "RECONCILIATION_REQUIRED";
            default -> "RESERVED";
        };
        LocalDateTime now = LocalDateTime.now();
        return new SpendingLimitReservationLifecycleClient.ReservationResponse(
                true,
                true,
                "USD",
                null,
                null,
                null,
                null,
                reservationId,
                transactionCorrelation,
                null,
                null,
                state,
                now,
                now,
                now.plusMinutes(30),
                outcome);
    }

    private String reservationFingerprint(String accountId, String userId, String operationType,
                                          BigDecimal amount, String currency, String idempotencyKey) {
        String canonical = String.join("|",
                canonicalAccountId(accountId),
                userId.trim(),
                operationType,
                amount.stripTrailingZeros().toPlainString(),
                currency,
                idempotencyKey);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String canonicalAccountId(String accountId) {
        String normalized = accountId == null ? "" : accountId.trim();
        try {
            return new BigInteger(normalized).toString();
        } catch (NumberFormatException notNumeric) {
            return normalized;
        }
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank()
                ? "USD" : currency.trim().toUpperCase(Locale.ROOT);
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
     * Get the WireMock server instance for stubbing Account Service integration testing.
     */
    protected WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
