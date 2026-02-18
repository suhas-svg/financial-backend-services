package com.suhasan.finance.transaction_service.aspect;

import com.suhasan.finance.transaction_service.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.UUID;

/**
 * Aspect for comprehensive audit logging of transaction operations
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLoggingAspect {
    
    private final AuditService auditService;
    
    /**
     * Pointcut for all controller methods
     */
    @Pointcut("execution(* com.suhasan.finance.transaction_service.controller.*.*(..))")
    public void controllerMethods() {}
    
    /**
     * Pointcut for all service methods
     */
    @Pointcut("execution(* com.suhasan.finance.transaction_service.service.*.*(..))")
    public void serviceMethods() {}
    
    /**
     * Pointcut for transaction processing methods
     */
    @Pointcut("execution(* com.suhasan.finance.transaction_service.service.TransactionServiceImpl.process*(..))")
    public void transactionProcessingMethods() {}
    
    /**
     * Around advice for controller methods to log API access
     */
    @Around("controllerMethods()")
    public Object logApiAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();
        
        // Set up MDC for the entire request
        MDC.put("correlationId", correlationId);
        MDC.put("component", "controller");
        
        try {
            // Get request details
            HttpServletRequest request = getCurrentRequest();
            String endpoint = null;
            String method = null;
            String ipAddress = null;
            String userId = null;
            
            if (request != null) {
                endpoint = request.getRequestURI();
                method = request.getMethod();
                ipAddress = getClientIpAddress(request);
                userId = extractUserIdFromRequest(request);
            }
            
            log.info("API request started: {} {} from {} user {}", 
                    method, endpoint, ipAddress, userId);
            
            // Proceed with the method execution
            Object result = joinPoint.proceed();
            
            // Log successful API access
            long responseTime = System.currentTimeMillis() - startTime;
            int responseStatus = 200; // Default success status
            
            auditService.logApiAccess(endpoint, method, userId, ipAddress, responseStatus, responseTime);
            
            log.info("API request completed: {} {} status {} time {}ms", 
                    method, endpoint, responseStatus, responseTime);
            
            return result;
            
        } catch (Exception e) {
            // Log failed API access
            long responseTime = System.currentTimeMillis() - startTime;
            int responseStatus = 500; // Default error status
            
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                auditService.logApiAccess(request.getRequestURI(), request.getMethod(), 
                        extractUserIdFromRequest(request), getClientIpAddress(request), 
                        responseStatus, responseTime);
            }
            
            log.error("API request failed: {} time {}ms error: {}", 
                    joinPoint.getSignature().getName(), responseTime, e.getMessage());
            
            throw e;
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Before advice for transaction processing methods
     */
    @Before("transactionProcessingMethods()")
    public void logTransactionStart(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        MDC.put("transactionMethod", methodName);
        MDC.put("component", "transaction-service");
        
        log.info("Transaction processing started: {} with args: {}", 
                methodName, Arrays.toString(args));
    }
    
    /**
     * After returning advice for transaction processing methods
     */
    @AfterReturning(pointcut = "transactionProcessingMethods()", returning = "result")
    public void logTransactionSuccess(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        
        log.info("Transaction processing completed successfully: {} result: {}", 
                methodName, result);
    }
    
    /**
     * After throwing advice for transaction processing methods
     */
    @AfterThrowing(pointcut = "transactionProcessingMethods()", throwing = "exception")
    public void logTransactionError(JoinPoint joinPoint, Throwable exception) {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        log.error("Transaction processing failed: {} with args: {} error: {}", 
                methodName, Arrays.toString(args), exception.getMessage(), exception);
        
        // Log security events for authentication/authorization failures
        if (exception instanceof org.springframework.security.access.AccessDeniedException) {
            HttpServletRequest request = getCurrentRequest();
            String userId = request != null ? extractUserIdFromRequest(request) : "unknown";
            String ipAddress = request != null ? getClientIpAddress(request) : "unknown";
            
            auditService.logSecurityEvent("ACCESS_DENIED", userId, 
                    "Access denied for method: " + methodName, ipAddress);
        }
    }
    
    /**
     * Around advice for service methods to track performance
     */
    @Around("serviceMethods() && !transactionProcessingMethods()")
    public Object logServiceMethodPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        
        MDC.put("serviceClass", className);
        MDC.put("serviceMethod", methodName);
        
        try {
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Log slow operations (over 1 second)
            if (executionTime > 1000) {
                log.warn("Slow service method execution: {}.{} took {}ms", 
                        className, methodName, executionTime);
            } else {
                log.debug("Service method executed: {}.{} took {}ms", 
                        className, methodName, executionTime);
            }
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Service method failed: {}.{} took {}ms error: {}", 
                    className, methodName, executionTime, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Get current HTTP request
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = 
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Extract user ID from request (from JWT token or headers)
     */
    private String extractUserIdFromRequest(HttpServletRequest request) {
        if (request == null) return "anonymous";
        
        // Try to get from custom header first
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        
        // Try to get from JWT token (this would need JWT parsing logic)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // In a real implementation, you would parse the JWT token here
            // For now, return a placeholder
            return "jwt-user";
        }
        
        return "anonymous";
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null) return "unknown";
        
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
