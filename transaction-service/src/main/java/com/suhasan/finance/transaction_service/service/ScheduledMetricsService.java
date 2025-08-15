package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service for scheduled metrics updates and maintenance tasks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledMetricsService {
    
    private final MetricsService metricsService;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;
    
    /**
     * Update pending transactions count every minute
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void updatePendingTransactionsCount() {
        try {
            long pendingCount = transactionRepository.countByStatus(TransactionStatus.PROCESSING);
            metricsService.updatePendingTransactionsCount(pendingCount);
            
            log.debug("Updated pending transactions count: {}", pendingCount);
        } catch (Exception e) {
            log.error("Failed to update pending transactions count", e);
        }
    }
    
    /**
     * Reset daily counters at midnight
     */
    @Scheduled(cron = "0 0 0 * * *") // Daily at midnight
    public void resetDailyCounters() {
        try {
            metricsService.resetDailyCounters();
            
            auditService.logSystemEvent("DAILY_RESET", "MetricsService", 
                    "Daily transaction counters reset", 
                    Map.of("timestamp", LocalDateTime.now().toString()));
            
            log.info("Daily transaction counters reset successfully");
        } catch (Exception e) {
            log.error("Failed to reset daily counters", e);
            auditService.logSystemEvent("DAILY_RESET_FAILED", "MetricsService", 
                    "Failed to reset daily counters: " + e.getMessage(), 
                    Map.of("error", e.getClass().getSimpleName()));
        }
    }
    
    /**
     * Log system health metrics every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void logSystemHealthMetrics() {
        try {
            double successRate = metricsService.getTransactionSuccessRate();
            long dailyVolume = metricsService.getDailyTransactionVolume();
            long activeTransactions = metricsService.getActiveTransactionsCount();
            
            auditService.logSystemEvent("HEALTH_METRICS", "TransactionService", 
                    "System health metrics collected", 
                    Map.of(
                            "successRate", String.format("%.2f%%", successRate * 100),
                            "dailyVolume", String.valueOf(dailyVolume),
                            "activeTransactions", String.valueOf(activeTransactions),
                            "timestamp", LocalDateTime.now().toString()
                    ));
            
            log.debug("System health metrics - Success Rate: {:.2f}%, Daily Volume: {}, Active: {}", 
                    successRate * 100, dailyVolume, activeTransactions);
                    
        } catch (Exception e) {
            log.error("Failed to log system health metrics", e);
        }
    }
    
    /**
     * Clean up old audit logs (weekly)
     */
    @Scheduled(cron = "0 0 2 * * SUN") // Weekly on Sunday at 2 AM
    public void cleanupOldAuditLogs() {
        try {
            // This would typically involve cleaning up old log files or database entries
            // For now, we'll just log the maintenance activity
            
            auditService.logSystemEvent("AUDIT_CLEANUP", "AuditService", 
                    "Weekly audit log cleanup initiated", 
                    Map.of("timestamp", LocalDateTime.now().toString()));
            
            log.info("Weekly audit log cleanup completed");
        } catch (Exception e) {
            log.error("Failed to cleanup audit logs", e);
        }
    }
    
    /**
     * Generate daily transaction summary report
     */
    @Scheduled(cron = "0 30 23 * * *") // Daily at 11:30 PM
    public void generateDailyTransactionSummary() {
        try {
            long dailyVolume = metricsService.getDailyTransactionVolume();
            double successRate = metricsService.getTransactionSuccessRate();
            
            auditService.logSystemEvent("DAILY_SUMMARY", "TransactionService", 
                    "Daily transaction summary generated", 
                    Map.of(
                            "dailyVolume", String.valueOf(dailyVolume),
                            "dailyAmount", metricsService.getDailyTransactionAmount().toString(),
                            "successRate", String.format("%.2f%%", successRate * 100),
                            "date", LocalDateTime.now().toLocalDate().toString()
                    ));
            
            log.info("Daily transaction summary - Volume: {}, Success Rate: {:.2f}%", 
                    dailyVolume, successRate * 100);
                    
        } catch (Exception e) {
            log.error("Failed to generate daily transaction summary", e);
        }
    }
}