package com.suhasan.finance.transaction_service.integration;

import com.suhasan.finance.transaction_service.security.UserTokenKeyLocator;
import com.suhasan.finance.transaction_service.security.keys.RemoteJwks;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import com.suhasan.finance.transaction_service.exception.ApiProblems;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class OperatorIamEnforcementFilter extends OncePerRequestFilter {
    private final String jwksUri;
    private final String publicKeyPem;
    private volatile UserTokenKeyLocator keys;
    private final String issuer;
    private final String audience;
    private final Set<String> mappedAdminClaims;
    private final boolean strict;
    private final long maxReviewAgeDays;

    public OperatorIamEnforcementFilter(
            @Value("${security.jwt.jwks-uri:}") String jwksUri,
            @Value("${security.jwt.public-key:}") String publicKeyPem,
            @Value("${integration.iam.issuer:local-account-service}") String issuer,
            @Value("${integration.iam.audience:transaction-service}") String audience,
            @Value("${integration.iam.role-mappings:admin=ROLE_ADMIN}") String mappings,
            @Value("${integration.iam.strict:false}") boolean strict,
            @Value("${integration.iam.access-review-max-age-days:90}") long maxReviewAgeDays) {
        this.jwksUri = jwksUri;
        this.publicKeyPem = publicKeyPem;
        this.issuer = issuer;
        this.audience = audience;
        this.strict = strict;
        this.maxReviewAgeDays = Math.max(1, maxReviewAgeDays);
        this.mappedAdminClaims = Arrays.stream(mappings.split(","))
                .map(String::trim).filter(v -> v.endsWith("=ROLE_ADMIN"))
                .map(v -> v.split("=", 2)[0]).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = Jwts.parser().keyLocator(keys())
                    .build().parseSignedClaims(header.substring(7)).getPayload();
            if (contains(claims.get("roles"), "ROLE_ADMIN")) validateOperator(claims);
            chain.doFilter(request, response);
        } catch (RuntimeException invalid) {
            ApiProblems.write(response, HttpStatus.UNAUTHORIZED, "operator-identity-invalid", "Unauthorized",
                    "Operator identity validation failed", request);
        }
    }

    // Built on first use, so a context without token configuration still starts.
    private UserTokenKeyLocator keys() {
        UserTokenKeyLocator locator = keys;
        if (locator == null) {
            synchronized (this) {
                if (keys == null) {
                    keys = new UserTokenKeyLocator(new RemoteJwks(jwksUri, publicKeyPem));
                }
                locator = keys;
            }
        }
        return locator;
    }

    private void validateOperator(Claims claims) {
        if (!strict) return;
        if (!issuer.equals(claims.getIssuer()) || claims.getAudience() == null
                || !claims.getAudience().contains(audience)) {
            throw new IllegalStateException("Operator issuer or audience mismatch");
        }
        if (Boolean.TRUE.equals(claims.get("revoked"))) throw new IllegalStateException("Operator is revoked");
        if (mappedAdminClaims.stream().noneMatch(role -> contains(claims.get("operator_roles"), role))) {
            throw new IllegalStateException("Operator role is not explicitly mapped");
        }
        Number reviewed = claims.get("access_reviewed_at", Number.class);
        if (reviewed == null || Instant.ofEpochSecond(reviewed.longValue())
                .plusSeconds(maxReviewAgeDays * 86400).isBefore(Instant.now())) {
            throw new IllegalStateException("Operator access review is missing or expired");
        }
    }

    private boolean contains(Object claim, String expected) {
        if (claim instanceof Collection<?> values) return values.stream().map(String::valueOf).anyMatch(expected::equals);
        return claim != null && List.of(String.valueOf(claim).split(",")).stream().map(String::trim).anyMatch(expected::equals);
    }
}
