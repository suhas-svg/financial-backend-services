package com.suhasan.finance.transaction_service.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Test configuration for integration tests
 */
@TestConfiguration
public class IntegrationTestConfiguration {

    private static final String JWT_SECRET = "AY8Ro0HSBFyllm9ZPafT2GWuE/t8Yzq1P0Rf7bNeq14=";
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
        private final SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());

        public String generateToken(String username) {
            return generateToken(username, new HashMap<>());
        }

        public String generateToken(String username, Map<String, Object> claims) {
            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + JWT_EXPIRATION);

            return Jwts.builder()
                    .setClaims(claims)
                    .setSubject(username)
                    .setIssuedAt(now)
                    .setExpiration(expiryDate)
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();
        }

        public String generateExpiredToken(String username) {
            Date now = new Date();
            Date expiryDate = new Date(now.getTime() - 1000); // Expired 1 second ago

            return Jwts.builder()
                    .setSubject(username)
                    .setIssuedAt(new Date(now.getTime() - 2000))
                    .setExpiration(expiryDate)
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();
        }

        public String generateTokenWithRole(String username, String role) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", role);
            claims.put("authorities", "ROLE_" + role);
            return generateToken(username, claims);
        }
    }
}