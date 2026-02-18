package com.suhasan.finance.transaction_service.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/**
 * Test configuration for integration tests
 */
@TestConfiguration
public class IntegrationTestConfiguration {

    private static final String JWT_SECRET = "testUserJwtSecretForUnitTests12345678901234567890";
    private static final long JWT_EXPIRATION = 3600000; // 1 hour

    /**
     * JWT Token utility for testing
     */
    @Bean
    @Primary
    public JwtTestUtil jwtTestUtil() {
        return new JwtTestUtil();
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
}
