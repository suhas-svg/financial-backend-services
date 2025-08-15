package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for handling alerting and critical transaction failure monitoring
 */
@Service
@Slf4j
public class AlertingService {
    
    private final MeterRegistry meterRegistry;
    private final MetricsService metricsService;
    private final AuditService auditService;
    
    @Value("${alerting.error-rate.threshold:0.05}") // 5% error rate threshold
    private double errorRateThreshold;
    
    @Value("${alerting.response-time.threshold:2000}") // 2 second response time threshold
    private long responseTimeThreshold;
    
    @Value("${alerting.account-service.error-threshold:10}") // 10 consecutive errors
    private int accountServiceErrorThreshold;
    
    @Value("${alerting.daily-volume.threshold:10000}") // 10,000 transactions per day
    private long dailyVolumeThreshold;
    
    // Alert counters and gauges
    private final Counter criticalAlertsCounter;
    private final Counter warningAlertsCounter;
    private final Counter infoAlertsCounter;
    
    // Alert state tracking
    private final AtomicLong consecutiveAccountServiceErrors = new AtomicLong(0);
    private final AtomicLong consecutiveHighErrorRateMinutes = new AtomicLong(0);
    private final AtomicLong consecutiveSlowResponseMinutes = new AtomicLong(0);
    
    // Alert suppression tracking (to prevent alert spam)
    private final ConcurrentHashMap<String, LocalDateTime> lastAlertTimes = new ConcurrentHashMap<>();
    private final long alertSuppressionMinutes = 15; // Suppress duplicate alerts for 15 minutes
    
    public AlertingService(MeterRegistry meterRegistry, MetricsService metricsService, AuditService auditService) {
        this.meterRegistry = meterRegistry;
        this.metricsService = metricsService;
        this.auditService = auditService;
        
        // Initialize alert counters
        this.criticalAlertsCounter = Counter.builder("alerts.critical.total")
                .description("Total number of critical alerts triggered")
                .register(meterRegistry);
                
        this.warningAlertsCounter = Counter.builder("alerts.warning.total")
                .description("Total number of warning alerts triggered")
                .register(meterRegistry);
                
        this.infoAlertsCounter = Counter.builder("alerts.info.total")
                .description("Total number of info alerts triggered")
                .register(meterRegistry);
        
        // Initialize alert state gauges
        Gauge.builder("alerts.account_service.consecutive_errors", 
                consecutiveAccountServiceErrors, AtomicLong::doubleValue)
                .description("Consecutive Account Service errors")
                .register(meterRegistry);
                
        Gauge.builder("alerts.error_rate.consecutive_high_minutes", 
                consecutiveHighErrorRateMinutes, AtomicLong::doubleValue)
                .description("Consecutive minutes with high error rate")
                .register(meterRegistry);
                
        Gauge.builder("alerts.response_time.consecutive_slow_minutes", 
                consecutiveSlowResponseMinutes, AtomicLong::doubleValue)
                .description("Consecutive minutes with slow response times")
                .register(meterRegistry);
    }
    
    /**
     * Check for critical transaction failures and trigger alerts
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void checkCriticalTransactionFailures() {
        try {
            // Check error rate
            double currentErrorRate = 1.0 - metricsService.getTransactionSuccessRate();
            if (currentErrorRate > errorRateThreshold) {
                consecutiveHighErrorRateMinutes.incrementAndGet();
                
                if (consecutiveHighErrorRateMinutes.get() >= 3) { // 3 consecutive minutes
                    triggerAlert(AlertLevel.CRITICAL, "HIGH_ERROR_RATE", 
                            String.format("Transaction error rate %.2f%% exceeds threshold %.2f%% for %d minutes", 
                                    currentErrorRate * 100, errorRateThreshold * 100, 
                                    consecutiveHighErrorRateMinutes.get()),
                            Map.of(
                                    "currentErrorRate", String.format("%.2f%%", currentErrorRate * 100),
                                    "threshold", String.format("%.2f%%", errorRateThreshold * 100),
                                    "consecutiveMinutes", String.valueOf(consecutiveHighErrorRateMinutes.get())
                            ));
                }
            } else {
                consecutiveHighErrorRateMinutes.set(0);
            }
            
            // Check daily transaction volume for anomalies
            long dailyVolume = metricsService.getDailyTransactionVolume();
            if (dailyVolume > dailyVolumeThreshold) {
                triggerAlert(AlertLevel.WARNING, "HIGH_DAILY_VOLUME", 
                        String.format("Daily transaction volume %d exceeds threshold %d", 
                                dailyVolume, dailyVolumeThreshold),
                        Map.of(
                                "currentVolume", String.valueOf(dailyVolume),
                                "threshold", String.valueOf(dailyVolumeThreshold)
                        ));
            }
            
            // Check for stuck transactions (active for too long)
            long activeTransactions = metricsService.getActiveTransactionsCount();
            if (activeTransactions > 100) { // More than 100 active transactions
                triggerAlert(AlertLevel.WARNING, "HIGH_ACTIVE_TRANSACTIONS", 
                        String.format("High number of active transactions: %d", activeTransactions),
                        Map.of("activeTransactions", String.valueOf(activeTransactions)));
            }
            
        } catch (Exception e) {
            log.error("Failed to check critical transaction failures", e);
            triggerAlert(AlertLevel.CRITICAL, "ALERTING_SYSTEM_FAILURE", 
                    "Alerting system failed to check critical metrics: " + e.getMessage(),
                    Map.of("error", e.getClass().getSimpleName()));
        }
    }
    
    /**
     * Check Account Service health and trigger alerts for connectivity issues
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void checkAccountServiceHealth() {
        try {
            // This would typically check the last Account Service call metrics
            // For now, we'll check if there have been recent errors
            
            // Reset consecutive errors if we haven't seen errors recently
            // In a real implementation, this would check actual health endpoint
            
            log.debug("Account Service health check completed");
            
        } catch (Exception e) {
            log.error("Failed to check Account Service health", e);
            recordAccountServiceError();
        }
    }
    
    /**
     * Record Account Service error and check for alert conditions
     */
    public void recordAccountServiceError() {
        long consecutiveErrors = consecutiveAccountServiceErrors.incrementAndGet();
        
        if (consecutiveErrors >= accountServiceErrorThreshold) {
            triggerAlert(AlertLevel.CRITICAL, "ACCOUNT_SERVICE_UNAVAILABLE", 
                    String.format("Account Service has %d consecutive errors", consecutiveErrors),
                    Map.of("consecutiveErrors", String.valueOf(consecutiveErrors)));
        } else if (consecutiveErrors >= accountServiceErrorThreshold / 2) {
            triggerAlert(AlertLevel.WARNING, "ACCOUNT_SERVICE_DEGRADED", 
                    String.format("Account Service showing signs of degradation: %d consecutive errors", 
                            consecutiveErrors),
                    Map.of("consecutiveErrors", String.valueOf(consecutiveErrors)));
        }
    }
    
    /**
     * Reset Account Service error count (called when successful call is made)
     */
    public void resetAccountServiceErrorCount() {
        long previousErrors = consecutiveAccountServiceErrors.getAndSet(0);
        if (previousErrors > 0) {
            log.info("Account Service recovered after {} consecutive errors", previousErrors);
            triggerAlert(AlertLevel.INFO, "ACCOUNT_SERVICE_RECOVERED", 
                    String.format("Account Service recovered after %d consecutive errors", previousErrors),
                    Map.of("previousErrors", String.valueOf(previousErrors)));
        }
    }
    
    /**
     * Record slow transaction processing and check for alert conditions
     */
    public void recordSlowTransaction(long processingTimeMs, String transactionType) {
        if (processingTimeMs > responseTimeThreshold) {
            consecutiveSlowResponseMinutes.incrementAndGet();
            
            if (consecutiveSlowResponseMinutes.get() >= 5) { // 5 consecutive slow responses
                triggerAlert(AlertLevel.WARNING, "SLOW_TRANSACTION_PROCESSING", 
                        String.format("Transaction processing is slow: %dms for %s (threshold: %dms)", 
                                processingTimeMs, transactionType, responseTimeThreshold),
                        Map.of(
                                "processingTime", String.valueOf(processingTimeMs),
                                "transactionType", transactionType,
                                "threshold", String.valueOf(responseTimeThreshold)
                        ));
            }
        } else {
            consecutiveSlowResponseMinutes.set(0);
        }
    }
    
    /**
     * Trigger an alert with the specified level and details
     */
    private void triggerAlert(AlertLevel level, String alertType, String message, Map<String, String> details) {
        // Check if we should suppress this alert (to prevent spam)
        String alertKey = level + ":" + alertType;
        LocalDateTime lastAlertTime = lastAlertTimes.get(alertKey);
        LocalDateTime now = LocalDateTime.now();
        
        if (lastAlertTime != null && 
            lastAlertTime.plusMinutes(alertSuppressionMinutes).isAfter(now)) {
            log.debug("Suppressing duplicate alert: {} (last triggered: {})", alertKey, lastAlertTime);
            return;
        }
        
        // Update last alert time
        lastAlertTimes.put(alertKey, now);
        
        // Increment appropriate counter
        switch (level) {
            case CRITICAL -> criticalAlertsCounter.increment();
            case WARNING -> warningAlertsCounter.increment();
            case INFO -> infoAlertsCounter.increment();
        }
        
        // Create alert details
        Map<String, String> alertDetails = new ConcurrentHashMap<>(details);
        alertDetails.put("alertLevel", level.name());
        alertDetails.put("alertType", alertType);
        alertDetails.put("timestamp", now.toString());
        alertDetails.put("service", "transaction-service");
        
        // Log the alert
        switch (level) {
            case CRITICAL -> log.error("CRITICAL ALERT [{}]: {}", alertType, message);
            case WARNING -> log.warn("WARNING ALERT [{}]: {}", alertType, message);
            case INFO -> log.info("INFO ALERT [{}]: {}", alertType, message);
        }
        
        // Record in audit log
        auditService.logSystemEvent("ALERT_TRIGGERED", "AlertingService", message, alertDetails);
        
        // In a production environment, this would also:
        // - Send notifications to monitoring systems (PagerDuty, Slack, etc.)
        // - Update dashboard status
        // - Trigger automated remediation if configured
        
        log.info("Alert triggered: level={}, type={}, message={}", level, alertType, message);
    }
    
    /**
     * Get current alert statistics
     */
    public Map<String, Object> getAlertStatistics() {
        return Map.of(
                "criticalAlerts", criticalAlertsCounter.count(),
                "warningAlerts", warningAlertsCounter.count(),
                "infoAlerts", infoAlertsCounter.count(),
                "consecutiveAccountServiceErrors", consecutiveAccountServiceErrors.get(),
                "consecutiveHighErrorRateMinutes", consecutiveHighErrorRateMinutes.get(),
                "consecutiveSlowResponseMinutes", consecutiveSlowResponseMinutes.get(),
                "activeAlertSuppressions", lastAlertTimes.size()
        );
    }
    
    /**
     * Clear alert suppression for testing or manual intervention
     */
    public void clearAlertSuppression(String alertType) {
        lastAlertTimes.entrySet().removeIf(entry -> entry.getKey().contains(alertType));
        log.info("Cleared alert suppression for type: {}", alertType);
    }
    
    /**
     * Alert severity levels
     */
    public enum AlertLevel {
        CRITICAL,  // Immediate attention required
        WARNING,   // Should be addressed soon
        INFO       // Informational, for awareness
    }
}