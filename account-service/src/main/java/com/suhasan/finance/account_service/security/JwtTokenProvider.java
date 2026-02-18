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
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.internal-secret}")
    private String internalJwtSecret;

    @Value("${security.jwt.expiration-in-ms}")
    private long jwtExpirationInMs;

    public String generateToken(Authentication auth) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(jwtExpirationInMs);
        List<String> roles = auth.getAuthorities().stream()
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

    public String getUsernameFromJWT(String token) {
        return parseUserClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        return parseUserClaimsOrNull(token) != null;
    }

    public boolean validateInternalServiceToken(String token) {
        Claims claims = parseInternalClaimsOrNull(token);
        if (claims == null) {
            return false;
        }

        Object tokenType = claims.get("token_type");
        if (!"service".equals(tokenType)) {
            return false;
        }

        String audience = claims.getAudience();
        if (!"account-service".equals(audience)) {
            return false;
        }

        List<String> roles = getInternalRoles(token);
        return roles.contains("ROLE_INTERNAL_SERVICE");
    }

    public String getInternalSubject(String token) {
        return parseInternalClaims(token).getSubject();
    }

    public List<String> getInternalRoles(String token) {
        Claims claims = parseInternalClaims(token);
        Object roleClaim = claims.get("roles");
        if (roleClaim instanceof List<?>) {
            return ((List<?>) roleClaim).stream().map(String::valueOf).collect(Collectors.toList());
        }
        return List.of();
    }

    private Claims parseUserClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Claims parseInternalClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(internalJwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Claims parseUserClaimsOrNull(String token) {
        try {
            return parseUserClaims(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    private Claims parseInternalClaimsOrNull(String token) {
        try {
            return parseInternalClaims(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
