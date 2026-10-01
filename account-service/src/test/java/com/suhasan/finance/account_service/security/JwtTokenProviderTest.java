package com.suhasan.finance.account_service.security;

import com.suhasan.finance.account_service.security.keys.RemoteJwks;
import com.suhasan.finance.account_service.security.keys.RsaSigningKeys;
import com.suhasan.finance.account_service.security.keys.TestKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        RsaSigningKeys signingKeys = new RsaSigningKeys("user access token",
                TestKeys.privatePem(TestKeys.USER.getPrivate()), "user-key-1", null, null, false);
        RemoteJwks internalKeys = new RemoteJwks(null, TestKeys.publicPem(TestKeys.INTERNAL.getPublic()));
        provider = new JwtTokenProvider(signingKeys, internalKeys, 60_000L);
    }

    private static UsernamePasswordAuthenticationToken alice() {
        return new UsernamePasswordAuthenticationToken("alice", "ignored", List.of(
                new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void generatedUserTokenIsRs256WithKeyIdSubjectRolesAndExpiration() {
        String token = provider.generateToken(alice());

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsernameFromJWT(token)).isEqualTo("alice");
        var jws = Jwts.parserBuilder().setSigningKey(TestKeys.USER.getPublic()).build().parseClaimsJws(token);
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("RS256");
        assertThat(jws.getHeader().getKeyId()).isEqualTo("user-key-1");
        Claims claims = jws.getBody();
        assertThat(claims.get("roles", List.class)).containsExactly("ROLE_USER", "ROLE_ADMIN");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void userValidationRejectsMalformedExpiredAndForeignKeyTokens() {
        String expired = userToken(TestKeys.USER.getPrivate(), "user-key-1", Instant.now().minusSeconds(60));
        String foreignKey = userToken(TestKeys.OTHER.getPrivate(), "user-key-1", Instant.now().plusSeconds(60));
        String unknownKid = userToken(TestKeys.USER.getPrivate(), "someone-else", Instant.now().plusSeconds(60));

        assertThat(provider.validateToken("not-a-jwt")).isFalse();
        assertThat(provider.validateToken(expired)).isFalse();
        assertThat(provider.validateToken(foreignKey)).isFalse();
        assertThat(provider.validateToken(unknownKid)).isFalse();
        assertThat(provider.validateToken(null)).isFalse();
    }

    @Test
    void hs256TokensAreRejectedEvenWhenTheHmacKeyIsThePublishedPublicKey() {
        // Algorithm confusion: an attacker signs HS256 using the public key as the "secret".
        byte[] publicKeyBytes = Base64.getEncoder().encode(TestKeys.USER.getPublic().getEncoded());
        String confused = Jwts.builder().setHeaderParam("kid", "user-key-1").setSubject("alice")
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(publicKeyBytes), SignatureAlgorithm.HS256).compact();
        String plainHs256 = Jwts.builder().setSubject("alice")
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor("x".repeat(40).getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider.validateToken(confused)).isFalse();
        assertThat(provider.validateToken(plainHs256)).isFalse();
    }

    @Test
    void tokensSignedWithTheRotatedOutKeyStillVerifyUntilTheyExpire() {
        RsaSigningKeys rotated = new RsaSigningKeys("user access token",
                TestKeys.privatePem(TestKeys.OTHER.getPrivate()), "user-key-2",
                TestKeys.publicPem(TestKeys.USER.getPublic()), "user-key-1", false);
        JwtTokenProvider afterRotation = new JwtTokenProvider(rotated,
                new RemoteJwks(null, TestKeys.publicPem(TestKeys.INTERNAL.getPublic())), 60_000L);

        String issuedBefore = provider.generateToken(alice());
        String issuedAfter = afterRotation.generateToken(alice());

        assertThat(afterRotation.validateToken(issuedBefore)).isTrue();
        assertThat(afterRotation.validateToken(issuedAfter)).isTrue();
        assertThat((List<?>) rotated.jwks().get("keys")).hasSize(2);
    }

    @Test
    void validInternalServiceTokenRequiresServiceTypeAudienceAndRole() {
        String token = internalToken("transaction-service", "service", "account-service",
                List.of("ROLE_INTERNAL_SERVICE"), TestKeys.INTERNAL.getPrivate());

        assertThat(provider.validateInternalServiceToken(token)).isTrue();
        assertThat(provider.getInternalSubject(token)).isEqualTo("transaction-service");
        assertThat(provider.getInternalRoles(token)).containsExactly("ROLE_INTERNAL_SERVICE");
    }

    @Test
    void internalValidationRejectsWrongTypeAudienceRoleAndSigner() {
        PrivateKey internal = TestKeys.INTERNAL.getPrivate();
        assertThat(provider.validateInternalServiceToken(internalToken(
                "transaction-service", "user", "account-service", List.of("ROLE_INTERNAL_SERVICE"), internal)))
                .isFalse();
        assertThat(provider.validateInternalServiceToken(internalToken(
                "transaction-service", "service", "other-service", List.of("ROLE_INTERNAL_SERVICE"), internal)))
                .isFalse();
        assertThat(provider.validateInternalServiceToken(internalToken(
                "transaction-service", "service", "account-service", List.of("ROLE_USER"), internal))).isFalse();
        // The user-token signer (this service) cannot mint internal service tokens.
        assertThat(provider.validateInternalServiceToken(internalToken(
                "transaction-service", "service", "account-service", List.of("ROLE_INTERNAL_SERVICE"),
                TestKeys.USER.getPrivate()))).isFalse();
        assertThat(provider.validateInternalServiceToken("invalid")).isFalse();
    }

    @Test
    void internalRolesAreEmptyWhenClaimIsNotAList() {
        String token = internalToken("transaction-service", "service", "account-service", "ROLE_INTERNAL_SERVICE",
                TestKeys.INTERNAL.getPrivate());

        assertThat(provider.getInternalRoles(token)).isEmpty();
        assertThat(provider.validateInternalServiceToken(token)).isFalse();
    }

    private static String userToken(PrivateKey key, String kid, Instant expiresAt) {
        return Jwts.builder().setHeaderParam("kid", kid).setSubject("alice")
                .setIssuedAt(Date.from(expiresAt.minusSeconds(120))).setExpiration(Date.from(expiresAt))
                .signWith(key, SignatureAlgorithm.RS256).compact();
    }

    private static String internalToken(String subject, String tokenType, String audience, Object roles,
                                        PrivateKey key) {
        return Jwts.builder()
                .setSubject(subject)
                .claim("token_type", tokenType)
                .setAudience(audience)
                .claim("roles", roles)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key, SignatureAlgorithm.RS256)
                .compact();
    }
}
