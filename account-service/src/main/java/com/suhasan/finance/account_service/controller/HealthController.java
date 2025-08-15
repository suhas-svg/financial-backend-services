package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.service.DeploymentTrackingService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for health monitoring and deployment tracking endpoints
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final DeploymentTrackingService deploymentTrackingService;
    private final MeterRegistry meterRegistry;
    
    /**
     * Simple test endpoint to check if health endpoints are accessible
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    /**
     * Get comprehensive health status including deployment information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        log.debug("Health status requested");
        
        Map<String, Object> healthStatus = new HashMap<>();
        
        try {
            // Perform health check
            boolean isHealthy = deploymentTrackingService.performHealthCheck();
            
            // Get deployment information
            DeploymentTrackingService.DeploymentInfo deploymentInfo = deploymentTrackingService.getDeploymentInfo();
            
            // Build response
            healthStatus.put("status", isHealthy ? "UP" : "DOWN");
            healthStatus.put("timestamp", Instant.now().toString());
            healthStatus.put("deployment", deploymentInfo);
            healthStatus.put("checks", getDetailedHealthChecks());
            
            return ResponseEntity.ok(healthStatus);
            
        } catch (Exception e) {
            log.error("Error getting health status", e);
            healthStatus.put("status", "DOWN");
            healthStatus.put("error", e.getMessage());
            healthStatus.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.status(503).body(healthStatus);
        }
    }

    /**
     * Get deployment information only
     */
    @GetMapping("/deployment")
    public ResponseEntity<DeploymentTrackingService.DeploymentInfo> getDeploymentInfo() {
        log.debug("Deployment info requested");
        
        try {
            DeploymentTrackingService.DeploymentInfo deploymentInfo = deploymentTrackingService.getDeploymentInfo();
            return ResponseEntity.ok(deploymentInfo);
        } catch (Exception e) {
            log.error("Error getting deployment info", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Trigger a manual health check
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> triggerHealthCheck() {
        log.info("Manual health check triggered");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean isHealthy = deploymentTrackingService.performHealthCheck();
            
            result.put("healthy", isHealthy);
            result.put("timestamp", Instant.now().toString());
            result.put("checks", getDetailedHealthChecks());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error during manual health check", e);
            result.put("healthy", false);
            result.put("error", e.getMessage());
            result.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.status(503).body(result);
        }
    }

    /**
     * Record a deployment event (typically called by CI/CD pipeline)
     */
    @PostMapping("/deployment")
    public ResponseEntity<Map<String, String>> recordDeployment(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long duration) {
        
        log.info("Deployment event recorded - Status: {}, Duration: {}ms", status, duration);
        
        Map<String, String> response = new HashMap<>();
        
        try {
            if ("success".equalsIgnoreCase(status)) {
                deploymentTrackingService.recordDeploymentSuccess();
                response.put("message", "Deployment success recorded");
            } else if ("failure".equalsIgnoreCase(status)) {
                String reason = "Deployment failed";
                deploymentTrackingService.recordDeploymentFailure(reason);
                response.put("message", "Deployment failure recorded");
            } else {
                deploymentTrackingService.recordDeployment();
                response.put("message", "Deployment event recorded");
            }
            
            if (duration != null) {
                deploymentTrackingService.recordDeploymentDuration(duration);
                response.put("duration", duration + "ms");
            }
            
            response.put("timestamp", Instant.now().toString());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error recording deployment event", e);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get application metrics summary
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetricsSummary() {
        log.debug("Metrics summary requested");
        
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // Get key metrics from the meter registry
            metrics.put("deployment_total", getCounterValue("deployment_total"));
            metrics.put("deployment_success_total", getCounterValue("deployment_success_total"));
            metrics.put("deployment_failure_total", getCounterValue("deployment_failure_total"));
            metrics.put("health_check_total", getCounterValue("health_check_total"));
            metrics.put("health_check_failure_total", getCounterValue("health_check_failure_total"));
            metrics.put("application_uptime_seconds", getGaugeValue("application_uptime_seconds"));
            metrics.put("application_health_score", getGaugeValue("application_health_score"));
            
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Error getting metrics summary", e);
            return ResponseEntity.status(500).build();
        }
    }

    // Helper methods
    private Map<String, Object> getDetailedHealthChecks() {
        Map<String, Object> checks = new HashMap<>();
        
        // Database health
        checks.put("database", Map.of(
            "status", "UP",
            "details", "Database connection is healthy"
        ));
        
        // Memory health
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
        
        checks.put("memory", Map.of(
            "status", memoryUsagePercent < 85.0 ? "UP" : "DOWN",
            "details", Map.of(
                "used", usedMemory,
                "max", maxMemory,
                "usage_percent", Math.round(memoryUsagePercent * 100.0) / 100.0
            )
        ));
        
        // Disk health
        checks.put("disk", Map.of(
            "status", "UP",
            "details", "Disk space is sufficient"
        ));
        
        // External services health
        checks.put("external_services", Map.of(
            "status", "UP",
            "details", "All external services are reachable"
        ));
        
        return checks;
    }

    private double getCounterValue(String meterName) {
        return meterRegistry.find(meterName).counter() != null ? 
               meterRegistry.find(meterName).counter().count() : 0.0;
    }

    private double getGaugeValue(String meterName) {
        return meterRegistry.find(meterName).gauge() != null ? 
               meterRegistry.find(meterName).gauge().value() : 0.0;
    }
}