package com.suhasan.finance.transaction_service.aspect;

import com.suhasan.finance.transaction_service.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLoggingAspect {
    private final AuditService auditService;

    @Pointcut("execution(* com.suhasan.finance.transaction_service.service.*.*(..))")
    public void serviceMethods() {}

    @Pointcut("execution(* com.suhasan.finance.transaction_service.service.TransactionServiceImpl.process*(..))")
    public void transactionProcessingMethods() {}

    @Before("transactionProcessingMethods()")
    public void logTransactionStart(JoinPoint joinPoint) {
        log.info("Transaction processing started: method={} actor={}",
                joinPoint.getSignature().getName(), actor());
    }

    @AfterThrowing(pointcut = "transactionProcessingMethods()", throwing = "exception")
    public void logTransactionError(JoinPoint joinPoint, Throwable exception) {
        log.error("Transaction processing failed: method={} actor={} errorType={}",
                joinPoint.getSignature().getName(), actor(), exception.getClass().getSimpleName());
        if (exception instanceof AccessDeniedException) {
            auditService.logSecurityEvent("ACCESS_DENIED", actor(),
                    "Access denied for " + joinPoint.getSignature().getName(), "captured-by-http-audit-filter");
        }
    }

    @Around("serviceMethods() && !transactionProcessingMethods()")
    public Object logServiceMethodPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - started;
            if (elapsed > 1000) {
                log.warn("Slow service method: class={} method={} durationMs={}",
                        joinPoint.getSignature().getDeclaringType().getSimpleName(),
                        joinPoint.getSignature().getName(), elapsed);
            }
        }
    }

    private String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                ? "anonymous" : authentication.getName();
    }
}
