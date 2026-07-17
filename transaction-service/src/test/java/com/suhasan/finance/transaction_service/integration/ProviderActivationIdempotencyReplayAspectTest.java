package com.suhasan.finance.transaction_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderActivationIdempotencyReplayAspectTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ProviderActivationService service = mock(ProviderActivationService.class);
    private final ProviderActivationIdempotencyReplayAspect aspect =
            new ProviderActivationIdempotencyReplayAspect(jdbcTemplate, objectMapper, service);

    @Test
    void returnsTheOriginalResultForAnIdenticalCertificationRetry() throws Throwable {
        ProviderActivationService.CertificationRequest request =
                new ProviderActivationService.CertificationRequest(
                        Set.of("DELIVERY"),
                        "evidence://sandbox/notification/delivery/1",
                        "evidence://sandbox/notification/sla/1",
                        "evidence://sandbox/notification/rollback/1",
                        "evidence://sandbox/notification/dr/1");
        ProviderActivationService.OperatorContext operator =
                new ProviderActivationService.OperatorContext(
                        "certifier-1",
                        "mfa://operator/certifier-1/assertion/1",
                        Instant.parse("2026-07-17T10:00:00Z"));
        Map<String, Object> original = Map.of("id", "activation-1");
        String fingerprint = fingerprint(Map.of(
                "action", "CERTIFY",
                "activationId", "activation-1",
                "request", request));

        when(jdbcTemplate.queryForList(
                anyString(),
                eq("certifier-1"),
                eq("retry-key-1")))
                .thenReturn(List.of(Map.of("request_fingerprint", fingerprint)));
        when(service.detail("activation-1")).thenReturn(original);

        ProceedingJoinPoint joinPoint = joinPoint(
                "certify",
                new Object[] {"activation-1", request, operator, "retry-key-1"});

        assertThat(aspect.replayLifecycle(joinPoint)).isSameAs(original);
        verify(joinPoint, never()).proceed();
    }

    @Test
    void rejectsReuseOfAnIdempotencyKeyWithDifferentEvidence() {
        ProviderActivationService.CertificationRequest request =
                new ProviderActivationService.CertificationRequest(
                        Set.of("RECONCILIATION"),
                        "evidence://sandbox/notification/reconciliation/2",
                        "evidence://sandbox/notification/sla/2",
                        "evidence://sandbox/notification/rollback/2",
                        "evidence://sandbox/notification/dr/2");
        ProviderActivationService.OperatorContext operator =
                new ProviderActivationService.OperatorContext(
                        "certifier-1",
                        "mfa://operator/certifier-1/assertion/1",
                        Instant.parse("2026-07-17T10:00:00Z"));

        when(jdbcTemplate.queryForList(
                anyString(),
                eq("certifier-1"),
                eq("retry-key-1")))
                .thenReturn(List.of(Map.of("request_fingerprint", "a-different-fingerprint")));

        ProceedingJoinPoint joinPoint = joinPoint(
                "certify",
                new Object[] {"activation-1", request, operator, "retry-key-1"});

        assertThatThrownBy(() -> aspect.replayLifecycle(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    private ProceedingJoinPoint joinPoint(String methodName, Object[] args) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    private String fingerprint(Object value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
