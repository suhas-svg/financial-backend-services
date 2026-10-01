package com.suhasan.finance.account_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Lets Prometheus scrape {@code /actuator/prometheus} with a dedicated static bearer
 * token (METRICS_SCRAPE_TOKEN) instead of a user or service JWT, which would expire.
 * The token grants only {@code ROLE_METRICS_SCRAPER}, which unlocks that one endpoint.
 * Without a configured token (or with one shorter than 32 characters) the filter does
 * nothing and the endpoint keeps requiring an admin or internal-service JWT.
 */
@Component
public class MetricsScrapeTokenFilter extends OncePerRequestFilter {

    static final String METRICS_PATH = "/actuator/prometheus";
    static final String ROLE = "ROLE_METRICS_SCRAPER";
    private static final int MIN_TOKEN_LENGTH = 32;
    private static final Logger LOG = LoggerFactory.getLogger(MetricsScrapeTokenFilter.class);

    private final byte[] expected;

    public MetricsScrapeTokenFilter(@Value("${management.prometheus.scrape-token:}") final String token) {
        final String trimmed = token == null ? "" : token.trim();
        if (!trimmed.isEmpty() && trimmed.length() < MIN_TOKEN_LENGTH) {
            LOG.warn("Ignoring METRICS_SCRAPE_TOKEN shorter than {} characters", MIN_TOKEN_LENGTH);
        }
        // Empty means "no token configured": the filter then stays out of the way.
        this.expected = trimmed.length() >= MIN_TOKEN_LENGTH ? trimmed.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    @Override
    protected boolean shouldNotFilter(@NonNull final HttpServletRequest request) {
        return expected.length == 0 || !METRICS_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull final HttpServletRequest request,
                                    @NonNull final HttpServletResponse response,
                                    @NonNull final FilterChain chain) throws ServletException, IOException {
        final String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && MessageDigest.isEqual(expected, header.substring(7).trim().getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "metrics-scraper", null, List.of(new SimpleGrantedAuthority(ROLE))));
        }
        chain.doFilter(request, response);
    }
}
