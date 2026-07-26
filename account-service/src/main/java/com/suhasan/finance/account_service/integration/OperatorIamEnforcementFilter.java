package com.suhasan.finance.account_service.integration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class OperatorIamEnforcementFilter extends OncePerRequestFilter {
    private final String secret;
    private final String issuer;
    private final String audience;
    private final Set<String> mappedAdminClaims;
    private final boolean strict;
    private final long maxReviewAgeDays;

    public OperatorIamEnforcementFilter(
            @Value("${security.jwt.secret}") final String secret,
            @Value("${integration.iam.issuer:local-account-service}") final String issuer,
            @Value("${integration.iam.audience:account-service}") final String audience,
            @Value("${integration.iam.role-mappings:admin=ROLE_ADMIN}") final String mappings,
            @Value("${integration.iam.strict:false}") final boolean strict,
            @Value("${integration.iam.access-review-max-age-days:90}") final long maxReviewAgeDays) {
        this.secret = secret;
        this.issuer = issuer;
        this.audience = audience;
        this.strict = strict;
        this.maxReviewAgeDays = Math.max(1, maxReviewAgeDays);
        this.mappedAdminClaims = Arrays.stream(mappings.split(","))
                .map(String::trim).filter(v -> v.endsWith("=ROLE_ADMIN"))
                .map(v -> v.split("=", 2)[0]).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
            throws ServletException, IOException {
        final String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        final Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build().parseClaimsJws(header.substring(7)).getBody();
        } catch (RuntimeException notAUserToken) {
            // Internal-service tokens use a separate key and are validated by
            // the existing authentication filter. Invalid tokens remain unauthenticated.
            chain.doFilter(request, response);
            return;
        }

        try {
            if (contains(claims.get("roles"), "ROLE_ADMIN")) {
                validateOperator(claims);
            }
            chain.doFilter(request, response);
        } catch (RuntimeException invalidOperator) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Operator identity validation failed");
        }
    }

    private void validateOperator(final Claims claims) {
        if (!strict) return;
        if (!issuer.equals(claims.getIssuer()) || !audience.equals(claims.getAudience())) {
            throw new IllegalStateException("Operator issuer or audience mismatch");
        }
        if (Boolean.TRUE.equals(claims.get("revoked"))) {
            throw new IllegalStateException("Operator is revoked");
        }
        if (mappedAdminClaims.stream().noneMatch(role -> contains(claims.get("operator_roles"), role))) {
            throw new IllegalStateException("Operator role is not explicitly mapped");
        }
        final Number reviewed = claims.get("access_reviewed_at", Number.class);
        if (reviewed == null || Instant.ofEpochSecond(reviewed.longValue())
                .plusSeconds(maxReviewAgeDays * 86400).isBefore(Instant.now())) {
            throw new IllegalStateException("Operator access review is missing or expired");
        }
    }

    private boolean contains(final Object claim, final String expected) {
        if (claim instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).anyMatch(expected::equals);
        }
        return claim != null && List.of(String.valueOf(claim).split(",")).stream()
                .map(String::trim).anyMatch(expected::equals);
    }
}
