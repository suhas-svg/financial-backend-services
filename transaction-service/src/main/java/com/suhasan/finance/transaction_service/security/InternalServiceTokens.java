package com.suhasan.finance.transaction_service.security;

import com.suhasan.finance.transaction_service.security.keys.RsaSigningKeys;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Mints the short-lived service tokens transaction-service presents to account-service's
 * internal API. They are RS256-signed with this service's own key; account-service verifies
 * them against the public keys published at this service's {@code /.well-known/jwks.json}.
 */
@Component
public class InternalServiceTokens {

    private static final long TTL_SECONDS = 60;

    private final RsaSigningKeys keys;

    @Autowired
    public InternalServiceTokens(
            @Value("${security.jwt.internal.signing.private-key:}") String privateKey,
            @Value("${security.jwt.internal.signing.key-id:}") String keyId,
            @Value("${security.jwt.internal.signing.previous-public-key:}") String previousPublicKey,
            @Value("${security.jwt.internal.signing.previous-key-id:}") String previousKeyId,
            @Value("${security.jwt.internal.signing.allow-generated-key:true}") boolean allowGeneratedKey) {
        this(new RsaSigningKeys("internal service token", privateKey, keyId, previousPublicKey, previousKeyId,
                allowGeneratedKey));
    }

    public InternalServiceTokens(RsaSigningKeys keys) {
        this.keys = keys;
    }

    /** A token for transaction-service acting as ROLE_INTERNAL_SERVICE towards account-service. */
    public String forAccountService() {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(keys.keyId()).and()
                .subject("transaction-service")
                // Plain string aud for compatibility with account-service's JWT parser.
                .claim("aud", "account-service")
                .claim("roles", List.of("ROLE_INTERNAL_SERVICE"))
                .claim("token_type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(TTL_SECONDS)))
                .signWith(keys.privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /** The JWK Set account-service verifies these tokens against. */
    public Map<String, Object> jwks() {
        return keys.jwks();
    }
}
