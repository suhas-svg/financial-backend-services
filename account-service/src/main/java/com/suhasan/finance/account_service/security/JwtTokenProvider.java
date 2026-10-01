package com.suhasan.finance.account_service.security;

import com.suhasan.finance.account_service.security.keys.RemoteJwks;
import com.suhasan.finance.account_service.security.keys.RsaSigningKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.UnsupportedJwtException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * User access tokens are RS256, signed with this service's private key and verified against its
 * published public keys. Internal service tokens are RS256 too, signed by transaction-service and
 * verified against transaction-service's JWKS. Any other algorithm (HS256 included) is rejected,
 * so a public key can never be used as an HMAC secret.
 */
@Component
public class JwtTokenProvider {

    private static final String RS256 = SignatureAlgorithm.RS256.getValue();

    private final RsaSigningKeys signingKeys;
    private final RemoteJwks internalKeys;
    private final long jwtExpirationInMs;

    public JwtTokenProvider(final RsaSigningKeys signingKeys,
                            @Qualifier("internalTokenVerificationKeys") final RemoteJwks internalKeys,
                            @Value("${security.jwt.expiration-in-ms}") final long jwtExpirationInMs) {
        this.signingKeys = signingKeys;
        this.internalKeys = internalKeys;
        this.jwtExpirationInMs = jwtExpirationInMs;
    }

    public String generateToken(final Authentication auth) {
        final Instant now = Instant.now();
        final Instant exp = now.plusMillis(jwtExpirationInMs);
        final List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .setHeaderParam("kid", signingKeys.keyId())
                .setSubject(auth.getName())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .claim("roles", roles)
                .signWith(signingKeys.privateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    public String getUsernameFromJWT(final String token) {
        return parseUserClaims(token).getSubject();
    }

    public boolean validateToken(final String token) {
        return parseUserClaimsIfValid(token).isPresent();
    }

    /** Verified claims of a user access token, for filters that read more than the subject. */
    public Optional<Claims> parseUserClaimsIfValid(final String token) {
        try {
            return Optional.of(parseUserClaims(token));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public boolean validateInternalServiceToken(final String token) {
        final Optional<Claims> parsedClaims = parseInternalClaimsIfValid(token);
        if (parsedClaims.isEmpty()) {
            return false;
        }
        final Claims claims = parsedClaims.orElseThrow();

        final Object tokenType = claims.get("token_type");
        if (!"service".equals(tokenType)) {
            return false;
        }

        final String audience = claims.getAudience();
        if (!"account-service".equals(audience)) {
            return false;
        }

        final List<String> roles = getInternalRoles(token);
        return roles.contains("ROLE_INTERNAL_SERVICE");
    }

    public String getInternalSubject(final String token) {
        return parseInternalClaims(token).getSubject();
    }

    public List<String> getInternalRoles(final String token) {
        final Claims claims = parseInternalClaims(token);
        final Object roleClaim = claims.get("roles");
        if (roleClaim instanceof List<?>) {
            return ((List<?>) roleClaim).stream().map(String::valueOf).collect(Collectors.toList());
        }
        return List.of();
    }

    private Claims parseUserClaims(final String token) {
        return parse(token, kid -> kid == null
                ? signingKeys.publicKey(signingKeys.keyId())
                : signingKeys.publicKey(kid));
    }

    private Claims parseInternalClaims(final String token) {
        return parse(token, internalKeys::resolve);
    }

    private Optional<Claims> parseInternalClaimsIfValid(final String token) {
        try {
            return Optional.of(parseInternalClaims(token));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Claims parse(final String token, final Function<String, Optional<? extends Key>> keys) {
        return Jwts.parserBuilder()
                .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                    @Override
                    public Key resolveSigningKey(final JwsHeader header, final Claims claims) {
                        if (!RS256.equals(header.getAlgorithm())) {
                            throw new UnsupportedJwtException("Only RS256 tokens are accepted");
                        }
                        return keys.apply(header.getKeyId())
                                .orElseThrow(() -> new UnsupportedJwtException("Unknown signing key"));
                    }
                })
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
