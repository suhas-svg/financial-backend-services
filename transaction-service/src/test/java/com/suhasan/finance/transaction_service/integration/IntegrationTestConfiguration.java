package com.suhasan.finance.transaction_service.integration;

import com.suhasan.finance.transaction_service.service.SpendingLimitReservationLifecycleClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test configuration for integration tests.
 */
@TestConfiguration
public class IntegrationTestConfiguration {

    private static final String JWT_SECRET = "testUserJwtSecretForUnitTests12345678901234567890";
    private static final long JWT_EXPIRATION = 3600000; // 1 hour

    /**
     * JWT Token utility for testing.
     */
    @Bean
    @Primary
    public JwtTestUtil jwtTestUtil() {
        return new JwtTestUtil();
    }

    /**
     * Payload-faithful reservation boundary for the legacy Spring integration suite.
     * Dedicated client integration tests still exercise the real HTTP lifecycle client.
     */
    @Bean
    @Primary
    public SpendingLimitReservationLifecycleClient spendingLimitReservationLifecycleClient(
            WebClient.Builder webClientBuilder) {
        return new IntegrationSpendingLimitReservationLifecycleClient(webClientBuilder);
    }

    public static class JwtTestUtil {
        private final SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

        public String generateToken(String username) {
            return generateToken(username, new HashMap<>());
        }

        public String generateToken(String username, Map<String, Object> claims) {
            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + JWT_EXPIRATION);
            Map<String, Object> tokenClaims = new HashMap<>(claims);
            tokenClaims.putIfAbsent("username", username);
            tokenClaims.putIfAbsent("userId", username);

            if (!tokenClaims.containsKey("roles")
                    && !tokenClaims.containsKey("role")
                    && !tokenClaims.containsKey("authorities")) {
                tokenClaims.put("roles", List.of("ROLE_USER"));
            }

            return Jwts.builder()
                    .claims(tokenClaims)
                    .subject(username)
                    .issuedAt(now)
                    .expiration(expiryDate)
                    .signWith(key)
                    .compact();
        }

        public String generateExpiredToken(String username) {
            Date now = new Date();
            Date expiryDate = new Date(now.getTime() - 1000); // Expired 1 second ago

            return Jwts.builder()
                    .subject(username)
                    .issuedAt(new Date(now.getTime() - 2000))
                    .expiration(expiryDate)
                    .signWith(key)
                    .compact();
        }

        public String generateTokenWithRole(String username, String role) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", role);
            claims.put("roles", List.of("ROLE_" + role));
            claims.put("authorities", "ROLE_" + role);
            return generateToken(username, claims);
        }
    }

    private static final class IntegrationSpendingLimitReservationLifecycleClient
            extends SpendingLimitReservationLifecycleClient {
        private static final BigDecimal DAILY_LIMIT = new BigDecimal("10000.00");
        private final AtomicLong reservationIds = new AtomicLong(10_000L);

        private IntegrationSpendingLimitReservationLifecycleClient(WebClient.Builder webClientBuilder) {
            super(webClientBuilder);
        }

        @Override
        public ReservationResponse reserve(String accountId, String operationType, BigDecimal amount,
                                           String idempotencyKey, String userId, String currency,
                                           String transactionCorrelation) {
            String normalizedOperation = operationType.trim().toUpperCase(Locale.ROOT);
            String normalizedCurrency = currency == null || currency.isBlank()
                    ? "USD" : currency.trim().toUpperCase(Locale.ROOT);
            BigDecimal normalizedAmount = amount.stripTrailingZeros();
            String normalizedKey = idempotencyKey.trim();
            long reservationId = reservationIds.incrementAndGet();
            LocalDateTime now = LocalDateTime.now();
            String correlation = transactionCorrelation == null || transactionCorrelation.isBlank()
                    ? "integration-reservation-" + reservationId : transactionCorrelation;
            String fingerprint = fingerprint(accountId, userId, normalizedOperation,
                    normalizedAmount, normalizedCurrency, normalizedKey);
            return new ReservationResponse(
                    true,
                    false,
                    normalizedCurrency,
                    DAILY_LIMIT,
                    BigDecimal.ZERO,
                    DAILY_LIMIT.subtract(normalizedAmount),
                    null,
                    reservationId,
                    correlation,
                    normalizedAmount,
                    fingerprint,
                    "RESERVED",
                    now,
                    now,
                    now.plusMinutes(30),
                    null);
        }

        @Override
        public ReservationResponse lookup(String accountId, String operationType,
                                          String idempotencyKey, String userId) {
            return null;
        }

        @Override
        public ReservationResponse transition(String accountId, Long reservationId, String action,
                                              String userId, String transactionCorrelation, String outcome) {
            String state = switch (action) {
                case "consume" -> "CONSUMED";
                case "release" -> "RELEASED";
                case "reconciliation-required" -> "RECONCILIATION_REQUIRED";
                default -> "RESERVED";
            };
            LocalDateTime now = LocalDateTime.now();
            return new ReservationResponse(
                    true,
                    true,
                    "USD",
                    DAILY_LIMIT,
                    BigDecimal.ZERO,
                    DAILY_LIMIT,
                    null,
                    reservationId,
                    transactionCorrelation,
                    BigDecimal.ZERO,
                    "integration-transition",
                    state,
                    now,
                    now,
                    now.plusMinutes(30),
                    outcome);
        }

        private String fingerprint(String accountId, String userId, String operationType,
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
                return new java.math.BigInteger(normalized).toString();
            } catch (NumberFormatException notNumeric) {
                return normalized;
            }
        }
    }
}
