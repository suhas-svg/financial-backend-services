package com.suhasan.finance.transaction_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Data transfer object for audit events
 */
@Data
@Builder
public class AuditEvent {
    
    private String eventId;
    private String eventType;
    private String action;
    private String userId;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private String resource;
    private String resourceId;
    private LocalDateTime timestamp;
    private String outcome;
    private String details;
    private Map<String, Object> metadata;
    private String correlationId;
    private String component;
    private String service;
    private String version;
    private String environment;
    
    // Transaction-specific fields
    private String transactionId;
    private String transactionType;
    private String transactionStatus;
    private String fromAccountId;
    private String toAccountId;
    private String amount;
    private String currency;
    
    // Performance fields
    private Long duration;
    private String responseCode;
    private Long responseSize;
    
    // Security fields
    private String authenticationMethod;
    private String authorizationResult;
    private String riskScore;
    
    // Error fields
    private String errorCode;
    private String errorMessage;
    private String stackTrace;
    
    public static class AuditEventBuilder {
        
        public AuditEventBuilder withDefaults() {
            this.eventId = java.util.UUID.randomUUID().toString();
            this.timestamp = LocalDateTime.now();
            this.service = "transaction-service";
            this.version = "1.0.0";
            this.environment = System.getProperty("spring.profiles.active", "local");
            return this;
        }
        
        public AuditEventBuilder forTransaction(String transactionId, String type, String status) {
            this.transactionId = transactionId;
            this.transactionType = type;
            this.transactionStatus = status;
            this.resource = "transaction";
            this.resourceId = transactionId;
            return this;
        }
        
        public AuditEventBuilder forAccount(String accountId) {
            this.resource = "account";
            this.resourceId = accountId;
            return this;
        }
        
        public AuditEventBuilder forApi(String endpoint, String method) {
            this.resource = "api";
            this.resourceId = method + " " + endpoint;
            this.action = "API_ACCESS";
            return this;
        }
        
        public AuditEventBuilder forSecurity(String eventType, String outcome) {
            this.eventType = "SECURITY";
            this.action = eventType;
            this.outcome = outcome;
            return this;
        }
        
        public AuditEventBuilder forSystem(String component, String action) {
            this.eventType = "SYSTEM";
            this.component = component;
            this.action = action;
            return this;
        }
        
        public AuditEventBuilder withUser(String userId, String sessionId, String ipAddress) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.ipAddress = ipAddress;
            return this;
        }
        
        public AuditEventBuilder withPerformance(Long duration, String responseCode) {
            this.duration = duration;
            this.responseCode = responseCode;
            return this;
        }
        
        public AuditEventBuilder withError(String errorCode, String errorMessage) {
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.outcome = "FAILURE";
            return this;
        }
        
        public AuditEventBuilder success() {
            this.outcome = "SUCCESS";
            return this;
        }
        
        public AuditEventBuilder failure() {
            this.outcome = "FAILURE";
            return this;
        }
    }
}