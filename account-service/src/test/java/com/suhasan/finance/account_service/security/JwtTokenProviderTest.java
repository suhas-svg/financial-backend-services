package com.suhasan.finance.account_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {
    private static final String USER_SECRET = "test-only-" + "0".repeat(32);
    private static final String INTERNAL_SECRET = "test-only-" + "1".repeat(32);

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", USER_SECRET);
        ReflectionTestUtils.setField(provider, "internalJwtSecret", INTERNAL_SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 60_000L);
    }

    @Test
    void generatedUserTokenContainsSubjectRolesAndValidExpiration() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "alice", "ignored", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")));

        String token = provider.generateToken(authentication);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsernameFromJWT(token)).isEqualTo("alice");
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseClaimsJws(token).getBody();
        assertThat(claims.get("roles", List.class)).containsExactly("ROLE_USER", "ROLE_ADMIN");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void userValidationRejectsMalformedExpiredAndDifferentlySignedTokens() {
        String expired = Jwts.builder()
                .setSubject("alice")
                .setIssuedAt(Date.from(Instant.now().minusSeconds(120)))
                .setExpiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        String wrongSignature = Jwts.builder()
                .setSubject("alice")
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(INTERNAL_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider.validateToken("not-a-jwt")).isFalse();
        assertThat(provider.validateToken(expired)).isFalse();
        assertThat(provider.validateToken(wrongSignature)).isFalse();
        assertThat(provider.validateToken(null)).isFalse();
    }

    @Test
    void validInternalServiceTokenRequiresServiceTypeAudienceAndRole() {
        String token = internalToken("transaction-service", "service", "account-service",
                List.of("ROLE_INTERNAL_SERVICE"));

        assertThat(provider.validateInternalServiceToken(token)).isTrue();
        assertThat(provider.getInternalSubject(token)).isEqualTo("transaction-service");
        assertThat(provider.getInternalRoles(token)).containsExactly("ROLE_INTERNAL_SERVICE");
    }

    @Test
    void internalValidationRejectsWrongTypeAudienceRoleAndSignature() {
        assertThat(provider.validateInternalServiceToken(internalToken(
                "transaction-service", "user", "account-service", List.of("ROLE_INTERNAL_SERVICE")))).isFalse();
        assertThat(provider.validateInternalServiceToken(internalToken(
                "transaction-service", "service", "other-service", List.of("ROLE_INTERNAL_SERVICE")))).isFalse();
        assertThat(provider.validateInternalServiceToken(internalToken(
                "transaction-service", "service", "account-service", List.of("ROLE_USER")))).isFalse();

        String userSigned = Jwts.builder()
                .setSubject("transaction-service")
                .claim("token_type", "service")
                .setAudience("account-service")
                .claim("roles", List.of("ROLE_INTERNAL_SERVICE"))
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(provider.validateInternalServiceToken(userSigned)).isFalse();
        assertThat(provider.validateInternalServiceToken("invalid")).isFalse();
    }

    @Test
    void internalRolesAreEmptyWhenClaimIsNotAList() {
        String token = internalToken("transaction-service", "service", "account-service", "ROLE_INTERNAL_SERVICE");

        assertThat(provider.getInternalRoles(token)).isEmpty();
        assertThat(provider.validateInternalServiceToken(token)).isFalse();
    }

    private String internalToken(String subject, String tokenType, String audience, Object roles) {
        return Jwts.builder()
                .setSubject(subject)
                .claim("token_type", tokenType)
                .setAudience(audience)
                .claim("roles", roles)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(INTERNAL_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
