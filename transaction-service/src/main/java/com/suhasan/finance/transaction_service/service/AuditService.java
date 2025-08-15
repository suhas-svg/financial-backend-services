package com.suhasan.finance.transaction_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.dto.AuditEvent;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Service for handling audit logging and transaction trails
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    
    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("AUDIT");
    private final ObjectMapper objectMapper;
    
    /**
     * Log transaction initiation
     */
    public void logTransactionInitiated(String transactionId, TransactionType type, 
                                      String fromAccountId, String toAccountId, 
                                      BigDecimal amount, String userId) {
        try {
            MDC.put("transactionId", transactionId);
            MDC.put("transactionType", type.toString());
            MDC.put("userId", userId);
            MDC.put("fromAccountId", fromAccountId != null ? fromAccountId : "N/A");
            MDC.put("toAccountId", toAccountId != null ? toAccountId : "N/A");
            MDC.put("amount", amount.toString());
            MDC.put("auditAction", "TRANSACTION_INITIATED");
            MDC.put("correlationId", UUID.randomUUID().toString());
            
            AUDIT_LOGGER.info("Transaction initiated: {} {} from {} to {} amount {}", 
                    transactionId, type, fromAccountId, toAccountId, amount);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log transaction completion
     */
    public void logTransactionCompleted(Transaction transaction) {
        try {
            MDC.put("transactionId", transaction.getTransactionId());
            MDC.put("transactionType", transaction.getType().toString());
            MDC.put("transactionStatus", transaction.getStatus().toString());
            MDC.put("userId", transaction.getCreatedBy());
            MDC.put("fromAccountId", transaction.getFromAccountId() != null ? transaction.getFromAccountId() : "N/A");
            MDC.put("toAccountId", transaction.getToAccountId() != null ? transaction.getToAccountId() : "N/A");
            MDC.put("amount", transaction.getAmount().toString());
            MDC.put("currency", transaction.getCurrency());
            MDC.put("auditAction", "TRANSACTION_COMPLETED");
            MDC.put("processingTime", calculateProcessingTime(transaction));
            
            AUDIT_LOGGER.info("Transaction completed successfully: {} status {} amount {} {}",
                    transaction.getTransactionId(), transaction.getStatus(), 
                    transaction.getAmount(), transaction.getCurrency());
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log transaction failure
     */
    public void logTransactionFailed(String transactionId, TransactionType type, 
                                   String fromAccountId, String toAccountId, 
                                   BigDecimal amount, String userId, String errorMessage, 
                                   String errorCode) {
        try {
            MDC.put("transactionId", transactionId);
            MDC.put("transactionType", type.toString());
            MDC.put("userId", userId);
            MDC.put("fromAccountId", fromAccountId != null ? fromAccountId : "N/A");
            MDC.put("toAccountId", toAccountId != null ? toAccountId : "N/A");
            MDC.put("amount", amount.toString());
            MDC.put("auditAction", "TRANSACTION_FAILED");
            MDC.put("errorMessage", errorMessage);
            MDC.put("errorCode", errorCode);
            
            AUDIT_LOGGER.error("Transaction failed: {} {} from {} to {} amount {} - Error: {} ({})",
                    transactionId, type, fromAccountId, toAccountId, amount, errorMessage, errorCode);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log transaction reversal
     */
    public void logTransactionReversal(String originalTransactionId, String reversalTransactionId, 
                                     String reason, String userId) {
        try {
            MDC.put("originalTransactionId", originalTransactionId);
            MDC.put("reversalTransactionId", reversalTransactionId);
            MDC.put("userId", userId);
            MDC.put("reversalReason", reason);
            MDC.put("auditAction", "TRANSACTION_REVERSED");
            
            AUDIT_LOGGER.info("Transaction reversed: original {} reversal {} reason: {}",
                    originalTransactionId, reversalTransactionId, reason);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log account validation
     */
    public void logAccountValidation(String accountId, String userId, boolean isValid, String reason) {
        try {
            MDC.put("accountId", accountId);
            MDC.put("userId", userId);
            MDC.put("validationResult", String.valueOf(isValid));
            MDC.put("auditAction", "ACCOUNT_VALIDATION");
            if (reason != null) {
                MDC.put("validationReason", reason);
            }
            
            AUDIT_LOGGER.info("Account validation: {} result {} reason: {}",
                    accountId, isValid, reason != null ? reason : "N/A");
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log balance check
     */
    public void logBalanceCheck(String accountId, BigDecimal requestedAmount, 
                              BigDecimal availableBalance, boolean sufficient, String userId) {
        try {
            MDC.put("accountId", accountId);
            MDC.put("userId", userId);
            MDC.put("requestedAmount", requestedAmount.toString());
            MDC.put("availableBalance", availableBalance.toString());
            MDC.put("sufficientFunds", String.valueOf(sufficient));
            MDC.put("auditAction", "BALANCE_CHECK");
            
            AUDIT_LOGGER.info("Balance check: account {} requested {} available {} sufficient {}",
                    accountId, requestedAmount, availableBalance, sufficient);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log transaction limit validation
     */
    public void logTransactionLimitCheck(String accountId, String accountType, TransactionType type,
                                       BigDecimal amount, boolean withinLimits, String limitType, 
                                       BigDecimal limitValue, String userId) {
        try {
            MDC.put("accountId", accountId);
            MDC.put("accountType", accountType);
            MDC.put("transactionType", type.toString());
            MDC.put("amount", amount.toString());
            MDC.put("withinLimits", String.valueOf(withinLimits));
            MDC.put("limitType", limitType);
            MDC.put("limitValue", limitValue.toString());
            MDC.put("userId", userId);
            MDC.put("auditAction", "LIMIT_CHECK");
            
            AUDIT_LOGGER.info("Transaction limit check: account {} type {} amount {} limit {} ({}) result {}",
                    accountId, type, amount, limitValue, limitType, withinLimits);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log security events
     */
    public void logSecurityEvent(String eventType, String userId, String details, String ipAddress) {
        try {
            MDC.put("userId", userId != null ? userId : "anonymous");
            MDC.put("securityEventType", eventType);
            MDC.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
            MDC.put("auditAction", "SECURITY_EVENT");
            if (details != null) {
                MDC.put("eventDetails", details);
            }
            
            AUDIT_LOGGER.warn("Security event: {} user {} from {} details: {}",
                    eventType, userId, ipAddress, details != null ? details : "N/A");
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log system events
     */
    public void logSystemEvent(String eventType, String component, String message, Map<String, String> additionalData) {
        try {
            MDC.put("systemEventType", eventType);
            MDC.put("component", component);
            MDC.put("auditAction", "SYSTEM_EVENT");
            
            if (additionalData != null) {
                additionalData.forEach(MDC::put);
            }
            
            AUDIT_LOGGER.info("System event: {} component {} message: {}",
                    eventType, component, message);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log API access
     */
    public void logApiAccess(String endpoint, String method, String userId, String ipAddress, 
                           int responseStatus, long responseTime) {
        try {
            MDC.put("endpoint", endpoint);
            MDC.put("httpMethod", method);
            MDC.put("userId", userId != null ? userId : "anonymous");
            MDC.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
            MDC.put("responseStatus", String.valueOf(responseStatus));
            MDC.put("responseTime", String.valueOf(responseTime));
            MDC.put("auditAction", "API_ACCESS");
            
            AUDIT_LOGGER.info("API access: {} {} user {} from {} status {} time {}ms",
                    method, endpoint, userId, ipAddress, responseStatus, responseTime);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log structured audit event
     */
    public void logAuditEvent(AuditEvent auditEvent) {
        try {
            String jsonEvent = objectMapper.writeValueAsString(auditEvent);
            
            // Set MDC with key audit fields
            MDC.put("eventId", auditEvent.getEventId());
            MDC.put("eventType", auditEvent.getEventType());
            MDC.put("action", auditEvent.getAction());
            MDC.put("userId", auditEvent.getUserId() != null ? auditEvent.getUserId() : "system");
            MDC.put("outcome", auditEvent.getOutcome());
            MDC.put("correlationId", auditEvent.getCorrelationId());
            
            if (auditEvent.getTransactionId() != null) {
                MDC.put("transactionId", auditEvent.getTransactionId());
            }
            
            AUDIT_LOGGER.info("Structured audit event: {}", jsonEvent);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit event", e);
            AUDIT_LOGGER.error("Failed to log structured audit event: {}", e.getMessage());
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Create and log transaction audit event
     */
    public void logTransactionAuditEvent(String action, Transaction transaction, String userId, String outcome) {
        AuditEvent auditEvent = AuditEvent.builder()
                .withDefaults()
                .forTransaction(transaction.getTransactionId(), 
                               transaction.getType().toString(), 
                               transaction.getStatus().toString())
                .withUser(userId, null, null)
                .eventType("TRANSACTION")
                .action(action)
                .outcome(outcome)
                .fromAccountId(transaction.getFromAccountId())
                .toAccountId(transaction.getToAccountId())
                .amount(transaction.getAmount().toString())
                .currency(transaction.getCurrency())
                .details(transaction.getDescription())
                .build();
        
        logAuditEvent(auditEvent);
    }
    
    /**
     * Calculate processing time for a transaction
     */
    private String calculateProcessingTime(Transaction transaction) {
        if (transaction.getCreatedAt() != null && transaction.getProcessedAt() != null) {
            long millis = java.time.Duration.between(transaction.getCreatedAt(), transaction.getProcessedAt()).toMillis();
            return millis + "ms";
        }
        return "unknown";
    }
}