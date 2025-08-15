package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.service.AlertingService;
import com.suhasan.finance.transaction_service.service.MetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for exposing monitoring and dashboard endpoints
 */
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
@Slf4j
public class MonitoringController {
    
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final MeterRegistry meterRegistry;
    
    /**
     * Get comprehensive system health status for dashboards
     */
    @GetMapping("/health/detailed")
    public ResponseEntity<Map<String, Object>> getDetailedHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Basic health metrics
            health.put("timestamp", LocalDateTime.now());
            health.put("service", "transaction-service");
            health.put("version", "1.0.0");
            health.put("status", "UP");
            
            // Transaction metrics
            Map<String, Object> transactionMetrics = new HashMap<>();
            transactionMetrics.put("successRate", String.format("%.2f%%", metricsService.getTransactionSuccessRate() * 100));
            transactionMetrics.put("dailyVolume", metricsService.getDailyTransactionVolume());
            transactionMetrics.put("dailyAmount", metricsService.getDailyTransactionAmount());
            transactionMetrics.put("activeTransactions", metricsService.getActiveTransactionsCount());
            health.put("transactions", transactionMetrics);
            
            // Alert statistics
            health.put("alerts", alertingService.getAlertStatistics());
            
            // System metrics
            Map<String, Object> systemMetrics = new HashMap<>();
            systemMetrics.put("jvmMemoryUsed", getJvmMemoryUsed());
            systemMetrics.put("jvmMemoryMax", getJvmMemoryMax());
            systemMetrics.put("systemCpuUsage", getSystemCpuUsage());
            systemMetrics.put("processUptime", getProcessUptime());
            health.put("system", systemMetrics);
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("Failed to get detailed health status", e);
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(500).body(health);
        }
    }
    
    /**
     * Get transaction processing statistics for dashboards
     */
    @GetMapping("/stats/transactions")
    public ResponseEntity<Map<String, Object>> getTransactionStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Current metrics
            stats.put("successRate", metricsService.getTransactionSuccessRate());
            stats.put("dailyVolume", metricsService.getDailyTransactionVolume());
            stats.put("dailyAmount", metricsService.getDailyTransactionAmount());
            stats.put("activeTransactions", metricsService.getActiveTransactionsCount());
            
            // Counter values
            stats.put("totalInitiated", getCounterValue("transaction.initiated.total"));
            stats.put("totalCompleted", getCounterValue("transaction.completed.total"));
            stats.put("totalFailed", getCounterValue("transaction.failed.total"));
            stats.put("totalReversed", getCounterValue("transaction.reversed.total"));
            
            // Error breakdown
            Map<String, Double> errors = new HashMap<>();
            errors.put("insufficientFunds", getCounterValue("transaction.error.insufficient_funds.total"));
            errors.put("accountNotFound", getCounterValue("transaction.error.account_not_found.total"));
            errors.put("limitExceeded", getCounterValue("transaction.error.limit_exceeded.total"));
            errors.put("accountServiceError", getCounterValue("account.service.error.total"));
            stats.put("errors", errors);
            
            // Performance metrics
            Map<String, Object> performance = new HashMap<>();
            performance.put("avgProcessingTime", getTimerMean("transaction.processing.duration"));
            performance.put("avgAccountValidationTime", getTimerMean("account.validation.duration"));
            performance.put("avgBalanceCheckTime", getTimerMean("balance.check.duration"));
            stats.put("performance", performance);
            
            stats.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Failed to get transaction statistics", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get system performance metrics for dashboards
     */
    @GetMapping("/stats/system")
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // JVM metrics
            Map<String, Object> jvm = new HashMap<>();
            jvm.put("memoryUsed", getJvmMemoryUsed());
            jvm.put("memoryMax", getJvmMemoryMax());
            jvm.put("memoryUsagePercent", getJvmMemoryUsagePercent());
            jvm.put("gcPauseTime", getTimerMean("jvm.gc.pause"));
            jvm.put("threadsActive", getGaugeValue("jvm.threads.live"));
            stats.put("jvm", jvm);
            
            // System metrics
            Map<String, Object> system = new HashMap<>();
            system.put("cpuUsage", getSystemCpuUsage());
            system.put("loadAverage", getGaugeValue("system.load.average.1m"));
            system.put("uptime", getProcessUptime());
            stats.put("system", system);
            
            // Database metrics
            Map<String, Object> database = new HashMap<>();
            database.put("connectionPoolActive", getGaugeValue("hikaricp.connections.active"));
            database.put("connectionPoolIdle", getGaugeValue("hikaricp.connections.idle"));
            database.put("connectionPoolMax", getGaugeValue("hikaricp.connections.max"));
            database.put("avgQueryTime", getTimerMean("database.operation.duration"));
            stats.put("database", database);
            
            // HTTP metrics
            Map<String, Object> http = new HashMap<>();
            http.put("requestsTotal", getCounterValue("http.server.requests"));
            http.put("avgResponseTime", getTimerMean("http.server.requests"));
            http.put("errorRate", getHttpErrorRate());
            stats.put("http", http);
            
            stats.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Failed to get system statistics", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get alert status and configuration
     */
    @GetMapping("/alerts/status")
    public ResponseEntity<Map<String, Object>> getAlertStatus() {
        try {
            Map<String, Object> alertStatus = new HashMap<>(alertingService.getAlertStatistics());
            alertStatus.put("timestamp", LocalDateTime.now());
            alertStatus.put("alertingEnabled", true);
            
            return ResponseEntity.ok(alertStatus);
            
        } catch (Exception e) {
            log.error("Failed to get alert status", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Clear alert suppression for a specific alert type (admin endpoint)
     */
    @PostMapping("/alerts/clear-suppression/{alertType}")
    public ResponseEntity<Map<String, String>> clearAlertSuppression(@PathVariable String alertType) {
        try {
            alertingService.clearAlertSuppression(alertType);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Alert suppression cleared for type: " + alertType,
                    "timestamp", LocalDateTime.now().toString()
            ));
            
        } catch (Exception e) {
            log.error("Failed to clear alert suppression for type: {}", alertType, e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get available metrics for dashboard configuration
     */
    @GetMapping("/metrics/available")
    public ResponseEntity<Map<String, Object>> getAvailableMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();
            
            // Get all meter names grouped by category
            Map<String, java.util.List<String>> metersByCategory = meterRegistry.getMeters().stream()
                    .map(meter -> meter.getId().getName())
                    .distinct()
                    .collect(Collectors.groupingBy(this::categorizeMetric));
            
            metrics.put("categories", metersByCategory);
            metrics.put("totalMeters", meterRegistry.getMeters().size());
            metrics.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Failed to get available metrics", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    // Helper methods for metric retrieval
    
    private double getCounterValue(String name) {
        return Search.in(meterRegistry).name(name).counter() != null ? 
                Search.in(meterRegistry).name(name).counter().count() : 0.0;
    }
    
    private double getGaugeValue(String name) {
        return Search.in(meterRegistry).name(name).gauge() != null ? 
                Search.in(meterRegistry).name(name).gauge().value() : 0.0;
    }
    
    private double getTimerMean(String name) {
        return Search.in(meterRegistry).name(name).timer() != null ? 
                Search.in(meterRegistry).name(name).timer().mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0;
    }
    
    private long getJvmMemoryUsed() {
        return (long) getGaugeValue("jvm.memory.used");
    }
    
    private long getJvmMemoryMax() {
        return (long) getGaugeValue("jvm.memory.max");
    }
    
    private double getJvmMemoryUsagePercent() {
        long used = getJvmMemoryUsed();
        long max = getJvmMemoryMax();
        return max > 0 ? (double) used / max * 100 : 0.0;
    }
    
    private double getSystemCpuUsage() {
        return getGaugeValue("system.cpu.usage") * 100; // Convert to percentage
    }
    
    private long getProcessUptime() {
        return (long) getGaugeValue("process.uptime");
    }
    
    private double getHttpErrorRate() {
        double totalRequests = getCounterValue("http.server.requests");
        double errorRequests = Search.in(meterRegistry)
                .name("http.server.requests")
                .tag("status", "5xx")
                .counters()
                .stream()
                .mapToDouble(counter -> counter.count())
                .sum();
        
        return totalRequests > 0 ? errorRequests / totalRequests : 0.0;
    }
    
    private String categorizeMetric(String metricName) {
        if (metricName.startsWith("transaction.")) return "transaction";
        if (metricName.startsWith("account.")) return "account";
        if (metricName.startsWith("jvm.")) return "jvm";
        if (metricName.startsWith("system.")) return "system";
        if (metricName.startsWith("http.")) return "http";
        if (metricName.startsWith("database.")) return "database";
        if (metricName.startsWith("alerts.")) return "alerts";
        if (metricName.startsWith("hikaricp.")) return "database";
        return "other";
    }
}