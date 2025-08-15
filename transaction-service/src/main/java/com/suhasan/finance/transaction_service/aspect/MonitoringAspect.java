package com.suhasan.finance.transaction_service.aspect;

import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.service.AlertingService;
import com.suhasan.finance.transaction_service.service.MetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Aspect for comprehensive monitoring and alerting integration
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class MonitoringAspect {
    
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final MeterRegistry meterRegistry;
    
    /**
     * Pointcut for Account Service client methods
     */
    @Pointcut("execution(* com.suhasan.finance.transaction_service.client.AccountServiceClient.*(..))")
    public void accountServiceMethods() {}
    
    /**
     * Pointcut for transaction processing methods
     */
    @Pointcut("execution(* com.suhasan.finance.transaction_service.service.TransactionServiceImpl.process*(..))")
    public void transactionProcessingMethods() {}
    
    /**
     * Pointcut for repository methods
     */
    @Pointcut("execution(* com.suhasan.finance.transaction_service.repository.*.*(..))")
    public void repositoryMethods() {}
    
    /**
     * Monitor Account Service calls and trigger alerts on failures
     */
    @Around("accountServiceMethods()")
    public Object monitorAccountServiceCalls(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        MDC.put("accountServiceMethod", methodName);
        
        try {
            log.debug("Account Service call started: {}", methodName);
            
            Object result = joinPoint.proceed();
            
            // Record successful call
            sample.stop(Timer.builder("account.service.call.duration")
                    .description("Account Service call duration")
                    .tag("method", methodName)
                    .tag("status", "success")
                    .register(meterRegistry));
            
            // Reset error count on successful call
            alertingService.resetAccountServiceErrorCount();
            
            log.debug("Account Service call completed successfully: {}", methodName);
            return result;
            
        } catch (Exception e) {
            // Record failed call
            sample.stop(Timer.builder("account.service.call.duration")
                    .description("Account Service call duration")
                    .tag("method", methodName)
                    .tag("status", "error")
                    .register(meterRegistry));
            
            // Record error for alerting
            alertingService.recordAccountServiceError();
            metricsService.recordAccountServiceError(methodName);
            
            log.error("Account Service call failed: {} - {}", methodName, e.getMessage());
            throw e;
            
        } finally {
            MDC.remove("accountServiceMethod");
        }
    }
    
    /**
     * Monitor transaction processing performance and trigger alerts on slow operations
     */
    @Around("transactionProcessingMethods()")
    public Object monitorTransactionProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Extract transaction type from method name or arguments
        String transactionType = extractTransactionType(methodName, joinPoint.getArgs());
        
        MDC.put("transactionProcessingMethod", methodName);
        MDC.put("transactionType", transactionType);
        
        try {
            log.debug("Transaction processing started: {} type: {}", methodName, transactionType);
            
            Object result = joinPoint.proceed();
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Record successful processing
            sample.stop(Timer.builder("transaction.processing.duration")
                    .description("Transaction processing duration")
                    .tag("method", methodName)
                    .tag("type", transactionType)
                    .tag("status", "success")
                    .register(meterRegistry));
            
            // Check for slow processing and trigger alerts
            alertingService.recordSlowTransaction(processingTime, transactionType);
            
            log.debug("Transaction processing completed: {} type: {} time: {}ms", 
                    methodName, transactionType, processingTime);
            
            return result;
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Record failed processing
            sample.stop(Timer.builder("transaction.processing.duration")
                    .description("Transaction processing duration")
                    .tag("method", methodName)
                    .tag("type", transactionType)
                    .tag("status", "error")
                    .register(meterRegistry));
            
            // Record processing failure
            metricsService.recordTransactionFailed(
                    TransactionType.valueOf(transactionType.toUpperCase()), 
                    e.getClass().getSimpleName()
            );
            
            log.error("Transaction processing failed: {} type: {} time: {}ms error: {}", 
                    methodName, transactionType, processingTime, e.getMessage());
            
            throw e;
            
        } finally {
            MDC.remove("transactionProcessingMethod");
            MDC.remove("transactionType");
        }
    }
    
    /**
     * Monitor database operations for performance
     */
    @Around("repositoryMethods()")
    public Object monitorDatabaseOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        MDC.put("repositoryClass", className);
        MDC.put("repositoryMethod", methodName);
        
        try {
            Object result = joinPoint.proceed();
            
            // Record successful database operation
            sample.stop(Timer.builder("database.operation.duration")
                    .description("Database operation duration")
                    .tag("repository", className)
                    .tag("method", methodName)
                    .tag("status", "success")
                    .register(meterRegistry));
            
            return result;
            
        } catch (Exception e) {
            // Record failed database operation
            sample.stop(Timer.builder("database.operation.duration")
                    .description("Database operation duration")
                    .tag("repository", className)
                    .tag("method", methodName)
                    .tag("status", "error")
                    .register(meterRegistry));
            
            // Increment database error counter
            meterRegistry.counter("database.operation.error.total",
                    "repository", className,
                    "method", methodName,
                    "error", e.getClass().getSimpleName())
                    .increment();
            
            log.error("Database operation failed: {}.{} - {}", className, methodName, e.getMessage());
            throw e;
            
        } finally {
            MDC.remove("repositoryClass");
            MDC.remove("repositoryMethod");
        }
    }
    
    /**
     * Extract transaction type from method name or arguments
     */
    private String extractTransactionType(String methodName, Object[] args) {
        // Extract from method name
        if (methodName.toLowerCase().contains("transfer")) {
            return "TRANSFER";
        } else if (methodName.toLowerCase().contains("deposit")) {
            return "DEPOSIT";
        } else if (methodName.toLowerCase().contains("withdraw")) {
            return "WITHDRAWAL";
        } else if (methodName.toLowerCase().contains("reverse")) {
            return "REVERSAL";
        }
        
        // Try to extract from arguments
        for (Object arg : args) {
            if (arg instanceof TransactionType) {
                return ((TransactionType) arg).name();
            }
            // Check if argument has a getType() method or similar
            if (arg != null) {
                String argString = arg.toString().toLowerCase();
                if (argString.contains("transfer")) return "TRANSFER";
                if (argString.contains("deposit")) return "DEPOSIT";
                if (argString.contains("withdraw")) return "WITHDRAWAL";
                if (argString.contains("reverse")) return "REVERSAL";
            }
        }
        
        return "UNKNOWN";
    }
}