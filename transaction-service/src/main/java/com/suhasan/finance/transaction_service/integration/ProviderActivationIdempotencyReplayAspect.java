package com.suhasan.finance.transaction_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes same-instance lifecycle retries before row-state validation and
 * returns the original result for a matching request fingerprint.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ProviderActivationIdempotencyReplayAspect {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ProviderActivationService service;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Around("execution(* com.suhasan.finance.transaction_service.integration.ProviderActivationService.certify(..))"
            + " || execution(* com.suhasan.finance.transaction_service.integration.ProviderActivationService.approve(..))"
            + " || execution(* com.suhasan.finance.transaction_service.integration.ProviderActivationService.activate(..))"
            + " || execution(* com.suhasan.finance.transaction_service.integration.ProviderActivationService.suspend(..))")
    public Object replayLifecycle(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String activationId = String.valueOf(args[0]);
        Object request = args[1];
        var operator = (ProviderActivationService.OperatorContext) args[2];
        String idempotencyKey = String.valueOf(args[3]).trim();
        String lockKey = operator.actor() + ":" + idempotencyKey;
        ReentrantLock lock = locks.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
        lock.lock();
        try {
            String action = joinPoint.getSignature().getName().toUpperCase();
            Object fingerprintValue = "SUSPEND".equals(action)
                    ? Map.of("action", action, "activationId", activationId,
                    "reason", ((ProviderActivationService.SuspensionRequest) request).reason().trim())
                    : Map.of("action", action, "activationId", activationId, "request", request);
            String fingerprint = fingerprint(fingerprintValue);
            List<Map<String, Object>> prior = jdbc.queryForList("""
                    SELECT request_fingerprint FROM provider_activation_events
                     WHERE actor=? AND idempotency_key=?
                    """, operator.actor(), idempotencyKey);
            if (!prior.isEmpty()) {
                if (!fingerprint.equals(prior.getFirst().get("request_fingerprint"))) {
                    throw new IllegalStateException(
                            "Idempotency-Key was reused for a different provider activation request");
                }
                return service.detail(activationId);
            }
            return joinPoint.proceed();
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) locks.remove(lockKey, lock);
        }
    }

    private String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(value)));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not fingerprint provider activation request", failure);
        }
    }
}
