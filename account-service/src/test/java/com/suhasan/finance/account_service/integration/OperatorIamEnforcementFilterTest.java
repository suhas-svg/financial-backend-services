package com.suhasan.finance.account_service.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorIamEnforcementFilterTest {
    private static final String SECRET = "unit-test-signing-secret-at-least-thirty-two-characters";

    @Test
    void adminTokenWithWrongIssuerFailsClosed() throws Exception {
        var filter = new OperatorIamEnforcementFilter(
                SECRET, "https://expected-idp", "account-service", "ops-admin=ROLE_ADMIN", true, 90);
        MockHttpServletRequest request = request(token("https://wrong-idp", "account-service", true));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void explicitlyMappedRecentlyReviewedOperatorPassesIamBoundary() throws Exception {
        var filter = new OperatorIamEnforcementFilter(
                SECRET, "https://expected-idp", "account-service", "ops-admin=ROLE_ADMIN", true, 90);
        MockHttpServletRequest request = request(token("https://expected-idp", "account-service", true));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tokenSignedForExistingInternalServiceBoundaryPassesThrough() throws Exception {
        var filter = new OperatorIamEnforcementFilter(
                SECRET, "https://expected-idp", "account-service", "ops-admin=ROLE_ADMIN", true, 90);
        MockHttpServletRequest request = request(tokenSignedWithDifferentKey());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private String token(String issuer, String audience, boolean reviewed) {
        Instant now = Instant.now();
        return Jwts.builder().setSubject("operator-1").setIssuer(issuer).setAudience(audience)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .claim("roles", List.of("ROLE_ADMIN"))
                .claim("operator_roles", List.of("ops-admin"))
                .claim("access_reviewed_at", reviewed ? now.getEpochSecond() : 0)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private String tokenSignedWithDifferentKey() {
        String internalSecret = "internal-service-secret-at-least-thirty-two-characters";
        Instant now = Instant.now();
        return Jwts.builder().setSubject("transaction-service").setAudience("account-service")
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .claim("roles", List.of("ROLE_INTERNAL_SERVICE"))
                .signWith(Keys.hmacShaKeyFor(internalSecret.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
    }
}
