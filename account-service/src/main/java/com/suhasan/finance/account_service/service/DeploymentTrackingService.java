package com.suhasan.finance.account_service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for tracking deployment metrics and health monitoring
 */
@Service
@Slf4j
public class DeploymentTrackingService {

    private final MeterRegistry meterRegistry;
    
    @Value("${app.version:unknown}")
    private String applicationVersion;
    
    @Value("${app.build-time:unknown}")
    private String buildTime;
    
    @Value("${app.git-commit:unknown}")
    private String gitCommit;
    
    // Deployment tracking metrics
    private Counter deploymentCounter;
    private Counter deploymentSuccessCounter;
    private Counter deploymentFailureCounter;
    private Timer deploymentDurationTimer;
    
    // Health monitoring metrics
    private Counter healthCheckCounter;
    private Counter healthCheckFailureCounter;
    private Timer healthCheckDurationTimer;
    
    // Internal state
    private final AtomicLong applicationStartTime = new AtomicLong();
    private final AtomicLong lastDeploymentTime = new AtomicLong();
    private final AtomicLong lastHealthCheckTime = new AtomicLong();
    private volatile double currentHealthScore = 100.0;

    public DeploymentTrackingService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initializeMetrics() {
        log.info("Initializing deployment tracking metrics...");
        
        // Record application start time
        applicationStartTime.set(Instant.now().toEpochMilli());
        lastDeploymentTime.set(Instant.now().toEpochMilli());
        
        // Initialize deployment tracking metrics
        deploymentCounter = Counter.builder("deployment_total")
                .description("Total number of deployments")
                .tag("version", applicationVersion)
                .tag("environment", getEnvironment())
                .register(meterRegistry);
                
        deploymentSuccessCounter = Counter.builder("deployment_success_total")
                .description("Total number of successful deployments")
                .tag("version", applicationVersion)
                .tag("environment", getEnvironment())
                .register(meterRegistry);
                
        deploymentFailureCounter = Counter.builder("deployment_failure_total")
                .description("Total number of failed deployments")
                .tag("version", applicationVersion)
                .tag("environment", getEnvironment())
                .register(meterRegistry);
                
        deploymentDurationTimer = Timer.builder("deployment_duration_seconds")
                .description("Time taken for deployment to complete")
                .tag("version", applicationVersion)
                .tag("environment", getEnvironment())
                .register(meterRegistry);
                
        // Initialize health monitoring metrics
        healthCheckCounter = Counter.builder("health_check_total")
                .description("Total number of health checks performed")
                .register(meterRegistry);
                
        healthCheckFailureCounter = Counter.builder("health_check_failure_total")
                .description("Total number of failed health checks")
                .register(meterRegistry);
                
        healthCheckDurationTimer = Timer.builder("health_check_duration_seconds")
                .description("Time taken to perform health checks")
                .register(meterRegistry);
        
        // Record successful deployment on startup
        recordDeploymentSuccess();
        
        log.info("Deployment tracking initialized - Version: {}, Build Time: {}, Git Commit: {}", 
                applicationVersion, buildTime, gitCommit);
    }

    /**
     * Record a deployment event
     */
    public void recordDeployment() {
        deploymentCounter.increment();
        lastDeploymentTime.set(Instant.now().toEpochMilli());
        log.info("Deployment event recorded for version: {}", applicationVersion);
    }

    /**
     * Record a successful deployment
     */
    public void recordDeploymentSuccess() {
        recordDeployment();
        deploymentSuccessCounter.increment();
        log.info("Successful deployment recorded for version: {}", applicationVersion);
    }

    /**
     * Record a failed deployment
     */
    public void recordDeploymentFailure(String reason) {
        recordDeployment();
        deploymentFailureCounter.increment();
        log.error("Failed deployment recorded for version: {} - Reason: {}", applicationVersion, reason);
    }

    /**
     * Record deployment duration
     */
    public void recordDeploymentDuration(long durationMillis) {
        deploymentDurationTimer.record(durationMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info("Deployment duration recorded: {}ms", durationMillis);
    }

    /**
     * Perform health check and record metrics
     */
    public boolean performHealthCheck() {
        return healthCheckDurationTimer.record(() -> {
            healthCheckCounter.increment();
            lastHealthCheckTime.set(Instant.now().toEpochMilli());
            
            try {
                // Perform comprehensive health checks
                boolean databaseHealthy = checkDatabaseHealth();
                boolean memoryHealthy = checkMemoryHealth();
                boolean diskHealthy = checkDiskHealth();
                boolean externalServicesHealthy = checkExternalServicesHealth();
                
                // Calculate health score
                double healthScore = calculateHealthScore(databaseHealthy, memoryHealthy, diskHealthy, externalServicesHealthy);
                currentHealthScore = healthScore;
                
                boolean overallHealthy = healthScore >= 80.0;
                
                if (!overallHealthy) {
                    healthCheckFailureCounter.increment();
                    log.warn("Health check failed - Score: {}", healthScore);
                } else {
                    log.debug("Health check passed - Score: {}", healthScore);
                }
                
                return overallHealthy;
                
            } catch (Exception e) {
                healthCheckFailureCounter.increment();
                currentHealthScore = 0.0;
                log.error("Health check failed with exception", e);
                return false;
            }
        });
    }

    /**
     * Get deployment information
     */
    public DeploymentInfo getDeploymentInfo() {
        return DeploymentInfo.builder()
                .version(applicationVersion)
                .buildTime(buildTime)
                .gitCommit(gitCommit)
                .environment(getEnvironment())
                .startTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(applicationStartTime.get()), ZoneOffset.UTC))
                .lastDeploymentTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(lastDeploymentTime.get()), ZoneOffset.UTC))
                .uptimeSeconds(getUptimeSeconds())
                .healthScore(currentHealthScore)
                .build();
    }

    // Helper methods for gauge calculations
    private double getUptimeSeconds() {
        return (Instant.now().toEpochMilli() - applicationStartTime.get()) / 1000.0;
    }

    private double getLastDeploymentTimestamp() {
        return lastDeploymentTime.get() / 1000.0;
    }

    private double getCurrentHealthScore() {
        return currentHealthScore;
    }

    private String getEnvironment() {
        return System.getProperty("spring.profiles.active", "dev");
    }

    // Health check implementations
    private boolean checkDatabaseHealth() {
        // This would typically check database connectivity
        // For now, return true as placeholder
        return true;
    }

    private boolean checkMemoryHealth() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
        return memoryUsagePercent < 85.0; // Healthy if memory usage < 85%
    }

    private boolean checkDiskHealth() {
        // This would typically check disk space
        // For now, return true as placeholder
        return true;
    }

    private boolean checkExternalServicesHealth() {
        // This would typically check external service connectivity
        // For now, return true as placeholder
        return true;
    }

    private double calculateHealthScore(boolean database, boolean memory, boolean disk, boolean externalServices) {
        double score = 0.0;
        
        if (database) score += 30.0;      // Database is critical - 30%
        if (memory) score += 25.0;        // Memory is important - 25%
        if (disk) score += 20.0;          // Disk space is important - 20%
        if (externalServices) score += 25.0; // External services - 25%
        
        return score;
    }

    // Inner class for deployment information
    @lombok.Builder
    @lombok.Data
    public static class DeploymentInfo {
        private String version;
        private String buildTime;
        private String gitCommit;
        private String environment;
        private LocalDateTime startTime;
        private LocalDateTime lastDeploymentTime;
        private double uptimeSeconds;
        private double healthScore;
    }
}