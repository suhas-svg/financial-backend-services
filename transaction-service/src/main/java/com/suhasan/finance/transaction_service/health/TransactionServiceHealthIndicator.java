package com.suhasan.finance.transaction_service.health;

import com.suhasan.finance.transaction_service.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.Map;

/**
 * Overall health indicator for Transaction Service
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceHealthIndicator implements HealthIndicator {
    
    private final MetricsService metricsService;
    
    @Override
    public Health health() {
        try {
            return checkTransactionServiceHealth();
        } catch (Exception e) {
            log.error("Transaction Service health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("service", "Transaction Service");
        }
    }
    
    private Health checkTransactionServiceHealth() {
        // Get JVM information
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        long uptime = runtimeBean.getUptime();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
        
        // Get transaction metrics
        long activeTransactions = metricsService.getActiveTransactionsCount();
        long dailyVolume = metricsService.getDailyTransactionVolume();
        double successRate = metricsService.getTransactionSuccessRate();
        
        // Determine health status based on metrics
        Health healthResult = Health.up();
        
        // Check memory usage
        if (memoryUsagePercent > 90) {
            healthResult = Health.down();
            healthResult.withDetail("memoryWarning", "High memory usage: " + String.format("%.2f%%", memoryUsagePercent));
        } else if (memoryUsagePercent > 80) {
            healthResult.withDetail("memoryWarning", "Elevated memory usage: " + String.format("%.2f%%", memoryUsagePercent));
        }
        
        // Check transaction success rate
        if (successRate < 0.95) { // Less than 95% success rate
            healthResult = Health.down();
            healthResult.withDetail("transactionWarning", "Low transaction success rate: " + String.format("%.2f%%", successRate * 100));
        }
        
        // Check active transactions (potential bottleneck)
        if (activeTransactions > 1000) {
            healthResult.withDetail("transactionWarning", "High number of active transactions: " + activeTransactions);
        }
        
        return healthResult
                .withDetail("service", "Transaction Service")
                .withDetail("version", "1.0.0")
                .withDetail("uptime", formatUptime(uptime))
                .withDetail("jvm", System.getProperty("java.version"))
                .withDetail("memory", Map.of(
                        "used", formatBytes(usedMemory),
                        "max", formatBytes(maxMemory),
                        "usagePercent", String.format("%.2f%%", memoryUsagePercent)
                ))
                .withDetail("transactions", Map.of(
                        "active", activeTransactions,
                        "dailyVolume", dailyVolume,
                        "successRate", String.format("%.2f%%", successRate * 100)
                ));
    }
    
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else {
            return String.format("%dm %ds", minutes, seconds % 60);
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}