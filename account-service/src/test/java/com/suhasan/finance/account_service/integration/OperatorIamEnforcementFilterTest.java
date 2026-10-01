package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.security.JwtTokenProvider;
import com.suhasan.finance.account_service.security.keys.RemoteJwks;
import com.suhasan.finance.account_service.security.keys.RsaSigningKeys;
import com.suhasan.finance.account_service.security.keys.TestKeys;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorIamEnforcementFilterTest {

    private static final String KEY_ID = "user-key-1";
    private final JwtTokenProvider tokens = new JwtTokenProvider(
            new RsaSigningKeys("user access token", TestKeys.privatePem(TestKeys.USER.getPrivate()), KEY_ID,
                    null, null, false),
            new RemoteJwks(null, TestKeys.publicPem(TestKeys.INTERNAL.getPublic())),
            60_000L);

    private OperatorIamEnforcementFilter strictFilter() {
        return new OperatorIamEnforcementFilter(
                tokens, "https://expected-idp", "account-service", "ops-admin=ROLE_ADMIN", true, 90);
    }

    @Test
    void adminTokenWithWrongIssuerFailsClosed() throws Exception {
        MockHttpServletRequest request = request(token("https://wrong-idp", "account-service", true));
        MockHttpServletResponse response = new MockHttpServletResponse();

        strictFilter().doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void explicitlyMappedRecentlyReviewedOperatorPassesIamBoundary() throws Exception {
        MockHttpServletRequest request = request(token("https://expected-idp", "account-service", true));
        MockHttpServletResponse response = new MockHttpServletResponse();

        strictFilter().doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tokenSignedForExistingInternalServiceBoundaryPassesThrough() throws Exception {
        MockHttpServletRequest request = request(internalServiceToken());
        MockHttpServletResponse response = new MockHttpServletResponse();

        strictFilter().doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private String token(String issuer, String audience, boolean reviewed) {
        Instant now = Instant.now();
        return Jwts.builder().setHeaderParam("kid", KEY_ID)
                .setSubject("operator-1").setIssuer(issuer).setAudience(audience)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .claim("roles", List.of("ROLE_ADMIN"))
                .claim("operator_roles", List.of("ops-admin"))
                .claim("access_reviewed_at", reviewed ? now.getEpochSecond() : 0)
                .signWith(TestKeys.USER.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
    }

    /** Signed by transaction-service's internal key, not the user-token key. */
    private String internalServiceToken() {
        Instant now = Instant.now();
        return Jwts.builder().setSubject("transaction-service").setAudience("account-service")
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .claim("roles", List.of("ROLE_INTERNAL_SERVICE"))
                .signWith(TestKeys.INTERNAL.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
    }
}
