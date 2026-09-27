package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.service.DeploymentTrackingService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
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
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Dependencies are injected and managed by Spring"
)
@SuppressWarnings({
        "PMD.AvoidDuplicateLiterals", // Stable response-schema keys intentionally repeat across endpoints.
        "PMD.AvoidLiteralsInIfCondition" // Deployment status values are protocol constants.
})
public class HealthController {

    private final DeploymentTrackingService deploymentTrackingService;
    private final MeterRegistry meterRegistry;

    /**
     * Optional: absent in {@code @WebMvcTest} slices, which do not auto-configure JDBC. When it is
     * missing the health checks report {@code UNKNOWN} rather than fabricating an "UP".
     */
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    private JdbcTemplate jdbcTemplate() {
        return jdbcTemplateProvider.getIfAvailable();
    }
    
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
        
        final Map<String, Object> healthStatus = new HashMap<>();
        
        try {
            // Perform health check
            final boolean isHealthy = deploymentTrackingService.performHealthCheck();
            
            // Get deployment information
            final DeploymentTrackingService.DeploymentInfo deploymentInfo = deploymentTrackingService.getDeploymentInfo();
            
            // Build response
            healthStatus.put("status", isHealthy ? "UP" : "DOWN");
            healthStatus.put("timestamp", Instant.now().toString());
            healthStatus.put("deployment", deploymentInfo);
            healthStatus.put("checks", getDetailedHealthChecks());
            
            return ResponseEntity.ok(healthStatus);
            
        } catch (Exception e) {
            log.error("Error getting health status", e);
            healthStatus.put("status", "DOWN");
            // Do not echo the raw exception message: /api/health/status is reachable without
            // authentication, and driver/connection messages can disclose host, port or DSN detail.
            healthStatus.put("error", "Health status could not be determined");
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
            final DeploymentTrackingService.DeploymentInfo deploymentInfo = deploymentTrackingService.getDeploymentInfo();
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
        
        final Map<String, Object> result = new HashMap<>();
        
        try {
            final boolean isHealthy = deploymentTrackingService.performHealthCheck();
            
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
            @RequestParam(required = false) final String status,
            @RequestParam(required = false) final Long duration) {
        
        log.info("Deployment event recorded - Status: {}, Duration: {}ms", status, duration);
        
        final Map<String, String> response = new HashMap<>();
        
        try {
            if ("success".equalsIgnoreCase(status)) {
                deploymentTrackingService.recordDeploymentSuccess();
                response.put("message", "Deployment success recorded");
            } else if ("failure".equalsIgnoreCase(status)) {
                final String reason = "Deployment failed";
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
        
        final Map<String, Object> metrics = new HashMap<>();
        
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
        final Map<String, Object> checks = new HashMap<>();

        // Database health — a real connectivity probe. This used to report a hard-coded "UP" with
        // the detail "Database connection is healthy", which asserted a check that never ran.
        checks.put("database", databaseHealthCheck());

        // Memory health
        final Runtime runtime = Runtime.getRuntime();
        final long maxMemory = runtime.maxMemory();
        final long totalMemory = runtime.totalMemory();
        final long freeMemory = runtime.freeMemory();
        final long usedMemory = totalMemory - freeMemory;
        final double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

        checks.put("memory", Map.of(
            "status", memoryUsagePercent < 85.0 ? "UP" : "DOWN",
            "details", Map.of(
                "used", usedMemory,
                "max", maxMemory,
                "usage_percent", Math.round(memoryUsagePercent * 100.0) / 100.0
            )
        ));

        // Disk health — a real usable-space probe rather than a hard-coded "UP".
        checks.put("disk", diskHealthCheck());

        // External service reachability is asserted by Spring Boot Actuator's own health
        // contributors; this controller does not probe them, so it must not claim they are UP.
        checks.put("external_services", Map.of(
            "status", "UNKNOWN",
            "details", "Not evaluated here. See /actuator/health for authoritative component health."
        ));

        return checks;
    }

    private Map<String, Object> databaseHealthCheck() {
        final JdbcTemplate jdbc = jdbcTemplate();
        if (jdbc == null) {
            return Map.of(
                    "status", "UNKNOWN",
                    "details", "No JDBC template available; database health not evaluated here.");
        }
        try {
            // A cheap validation query: if the datasource cannot produce a connection, this throws.
            final Integer one = jdbc.queryForObject("select 1", Integer.class);
            return "1".equals(String.valueOf(one))
                    ? Map.of("status", "UP", "details", "Database connection is healthy")
                    : Map.of("status", "DOWN", "details", "Unexpected database probe result");
        } catch (Exception e) {
            // Report the failure category only; the raw driver message can contain connection
            // details that must not be exposed to an unauthenticated caller of /api/health/status.
            log.warn("Database health probe failed", e);
            return Map.of(
                    "status", "DOWN",
                    "details", "Database is not reachable",
                    "error_type", e.getClass().getSimpleName());
        }
    }

    private Map<String, Object> diskHealthCheck() {
        final File file = new File(".");
        try {
            final long usable = file.getUsableSpace();
            final long total = file.getTotalSpace();
            final double usedPercent = total > 0 ? ((double) (total - usable) / total) * 100.0 : 0.0;
            return Map.of(
                    "status", usedPercent < 90.0 ? "UP" : "DOWN",
                    "details", Map.of(
                            "path", file.getAbsolutePath(),
                            "usable_bytes", usable,
                            "total_bytes", total,
                            "used_percent", Math.round(usedPercent * 100.0) / 100.0));
        } catch (Exception e) {
            log.warn("Disk health probe failed", e);
            return Map.of(
                    "status", "UNKNOWN",
                    "details", "Disk space could not be determined",
                    "error_type", e.getClass().getSimpleName());
        }
    }

    private double getCounterValue(final String meterName) {
        final Counter counter = meterRegistry.find(meterName).counter();
        return counter != null ? counter.count() : 0.0;
    }

    private double getGaugeValue(final String meterName) {
        final Gauge gauge = meterRegistry.find(meterName).gauge();
        return gauge != null ? gauge.value() : 0.0;
    }
}
