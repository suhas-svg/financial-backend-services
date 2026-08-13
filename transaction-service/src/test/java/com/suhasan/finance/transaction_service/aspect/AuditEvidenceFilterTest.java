package com.suhasan.finance.transaction_service.aspect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuditEvidenceFilterTest {
    private final ApiAccessAuditRecorder auditRecorder = mock(ApiAccessAuditRecorder.class);
    private final AuditEvidenceFilter filter = new AuditEvidenceFilter(
            new StaticListableBeanFactory(java.util.Map.of("auditRecorder", auditRecorder))
                    .getBeanProvider(ApiAccessAuditRecorder.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usesAuthenticatedPrincipalActualStatusAndRejectsForgedIdentityAndForwardedIp() throws Exception {
        ReflectionTestUtils.setField(filter, "trustedProxyEnabled", false);
        ReflectionTestUtils.setField(filter, "trustedProxyAddresses", "");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("principal-1", "secret-proof", List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/transactions/transfer");
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-User-Id", "forged-user");
        request.addHeader("X-Forwarded-For", "203.0.113.7");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(422));

        verify(auditRecorder).record(
                eq("/api/transactions/transfer"), eq("POST"), eq("principal-1"), eq("10.0.0.5"), eq(422),
                longThat(value -> value >= 0));
    }

    @Test
    void excludesHealthProbeFromAuditPersistence() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        verifyNoInteractions(auditRecorder);
    }
    @Test
    void honorsForwardedIpOnlyForAnExplicitlyTrustedProxy() throws Exception {
        ReflectionTestUtils.setField(filter, "trustedProxyEnabled", true);
        ReflectionTestUtils.setField(filter, "trustedProxyAddresses", "10.0.0.5");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(204));

        verify(auditRecorder).record(
                eq("/api/accounts"), eq("GET"), eq("anonymous"), eq("203.0.113.7"), eq(204),
                longThat(value -> value >= 0));
    }
}
