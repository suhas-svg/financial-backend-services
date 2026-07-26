package com.suhasan.finance.account_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.internal-secret}")
    private String internalJwtSecret;

    @Value("${security.jwt.expiration-in-ms}")
    private long jwtExpirationInMs;

    public String generateToken(final Authentication auth) {
        final Instant now = Instant.now();
        final Instant exp = now.plusMillis(jwtExpirationInMs);
        final List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .setSubject(auth.getName())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .claim("roles", roles)
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromJWT(final String token) {
        return parseUserClaims(token).getSubject();
    }

    public boolean validateToken(final String token) {
        return parseUserClaimsIfValid(token).isPresent();
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
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Claims parseInternalClaims(final String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(internalJwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Optional<Claims> parseUserClaimsIfValid(final String token) {
        try {
            return Optional.of(parseUserClaims(token));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<Claims> parseInternalClaimsIfValid(final String token) {
        try {
            return Optional.of(parseInternalClaims(token));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
