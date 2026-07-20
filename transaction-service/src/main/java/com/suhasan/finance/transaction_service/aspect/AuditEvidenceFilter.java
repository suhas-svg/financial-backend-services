package com.suhasan.finance.transaction_service.aspect;

import com.suhasan.finance.transaction_service.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuditEvidenceFilter extends OncePerRequestFilter {
    private final ObjectProvider<AuditService> auditService;

    @Value("${audit.trusted-proxy.enabled:false}")
    private boolean trustedProxyEnabled;

    @Value("${audit.trusted-proxy.addresses:}")
    private String trustedProxyAddresses;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String actor = authentication == null || !authentication.isAuthenticated()
                    ? "anonymous" : authentication.getName();
            AuditService service = auditService.getIfAvailable();
            if (service != null) {
                service.logApiAccess(request.getRequestURI(), request.getMethod(), actor,
                        clientIp(request), response.getStatus(), System.currentTimeMillis() - started);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/actuator/health".equals(request.getRequestURI());
    }
    private String clientIp(HttpServletRequest request) {
        Set<String> trusted = Arrays.stream(trustedProxyAddresses.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toSet());
        if (trustedProxyEnabled && trusted.contains(request.getRemoteAddr())) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
