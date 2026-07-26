package com.suhasan.finance.account_service.config;

import com.suhasan.finance.account_service.service.DeploymentTrackingService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.actuate.info.Info;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for health monitoring and custom health indicators
 */
@Configuration
@SuppressWarnings("PMD.AvoidDuplicateLiterals") // Stable health-evidence JSON field names are clearer inline.
public class HealthMonitoringConfig {

    /**
     * Custom health indicator for deployment tracking
     */
    @Bean
    public HealthIndicator deploymentHealthIndicator(final DeploymentTrackingService deploymentTrackingService) {
        return () -> {
            try {
                final DeploymentTrackingService.DeploymentInfo deploymentInfo = deploymentTrackingService.getDeploymentInfo();
                
                final Map<String, Object> details = new HashMap<>();
                details.put("version", deploymentInfo.getVersion());
                details.put("environment", deploymentInfo.getEnvironment());
                details.put("uptime_seconds", deploymentInfo.getUptimeSeconds());
                details.put("health_score", deploymentInfo.getHealthScore());
                details.put("last_deployment", deploymentInfo.getLastDeploymentTime());
                
                // Determine health status based on health score
                final Status status = deploymentInfo.getHealthScore() >= 80.0 ? Status.UP : Status.DOWN;
                
                return Health.status(status)
                        .withDetails(details)
                        .build();
                        
            } catch (Exception e) {
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    /**
     * Enhanced database health indicator with connection pool metrics
     */
    @Bean
    public HealthIndicator enhancedDatabaseHealthIndicator(final DataSource dataSource) {
        return () -> {
            try (Connection connection = dataSource.getConnection()) {
                final Map<String, Object> details = new HashMap<>();
                details.put("database", connection.getMetaData().getDatabaseProductName());
                details.put("version", connection.getMetaData().getDatabaseProductVersion());
                details.put("url", connection.getMetaData().getURL());
                details.put("connection_valid", connection.isValid(5));
                details.put("check_timestamp", Instant.now());
                
                // Additional connection pool metrics would be added here
                // This would typically integrate with HikariCP metrics
                
                return Health.up()
                        .withDetails(details)
                        .build();
                        
            } catch (SQLException e) {
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .withDetail("check_timestamp", Instant.now())
                        .build();
            }
        };
    }

    /**
     * Memory health indicator with detailed JVM metrics
     */
    @Bean
    public HealthIndicator memoryHealthIndicator() {
        return () -> {
            final Runtime runtime = Runtime.getRuntime();
            final long maxMemory = runtime.maxMemory();
            final long totalMemory = runtime.totalMemory();
            final long freeMemory = runtime.freeMemory();
            final long usedMemory = totalMemory - freeMemory;
            
            final double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            
            final Map<String, Object> details = new HashMap<>();
            details.put("max_memory_bytes", maxMemory);
            details.put("total_memory_bytes", totalMemory);
            details.put("free_memory_bytes", freeMemory);
            details.put("used_memory_bytes", usedMemory);
            details.put("usage_percent", Math.round(memoryUsagePercent * 100.0) / 100.0);
            details.put("available_processors", runtime.availableProcessors());
            details.put("check_timestamp", Instant.now());
            
            // Memory is considered healthy if usage is below 85%
            final Status status = memoryUsagePercent < 85.0 ? Status.UP : Status.DOWN;
            
            return Health.status(status)
                    .withDetails(details)
                    .build();
        };
    }

    /**
     * Disk space health indicator
     */
    @Bean
    public HealthIndicator diskSpaceHealthIndicator() {
        return () -> {
            try {
                final java.io.File[] roots = java.io.File.listRoots();
                final java.io.File root = roots.length > 0 ? roots[0] : new java.io.File(".");
                final long totalSpace = root.getTotalSpace();
                final long freeSpace = root.getFreeSpace();
                final long usedSpace = totalSpace - freeSpace;
                final double usagePercent = (double) usedSpace / totalSpace * 100;
                
                final Map<String, Object> details = new HashMap<>();
                details.put("total_space_bytes", totalSpace);
                details.put("free_space_bytes", freeSpace);
                details.put("used_space_bytes", usedSpace);
                details.put("usage_percent", Math.round(usagePercent * 100.0) / 100.0);
                details.put("check_timestamp", Instant.now());
                
                // Disk is considered healthy if usage is below 90%
                final Status status = usagePercent < 90.0 ? Status.UP : Status.DOWN;
                
                return Health.status(status)
                        .withDetails(details)
                        .build();
                        
            } catch (Exception e) {
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .withDetail("check_timestamp", Instant.now())
                        .build();
            }
        };
    }

    /**
     * Custom info contributor for deployment information
     */
    @Bean
    public InfoContributor deploymentInfoContributor(final DeploymentTrackingService deploymentTrackingService) {
        return (Info.Builder builder) -> {
            try {
                final DeploymentTrackingService.DeploymentInfo deploymentInfo = deploymentTrackingService.getDeploymentInfo();
                
                final Map<String, Object> deploymentDetails = new HashMap<>();
                deploymentDetails.put("version", deploymentInfo.getVersion());
                deploymentDetails.put("build_time", deploymentInfo.getBuildTime());
                deploymentDetails.put("git_commit", deploymentInfo.getGitCommit());
                deploymentDetails.put("environment", deploymentInfo.getEnvironment());
                deploymentDetails.put("start_time", deploymentInfo.getStartTime());
                deploymentDetails.put("last_deployment_time", deploymentInfo.getLastDeploymentTime());
                deploymentDetails.put("uptime_seconds", deploymentInfo.getUptimeSeconds());
                deploymentDetails.put("health_score", deploymentInfo.getHealthScore());
                
                builder.withDetail("deployment", deploymentDetails);
                
            } catch (Exception e) {
                builder.withDetail("deployment", Map.of("error", e.getMessage()));
            }
        };
    }

    /**
     * System info contributor for runtime information
     */
    @Bean
    public InfoContributor systemInfoContributor() {
        return (Info.Builder builder) -> {
            final Map<String, Object> systemInfo = new HashMap<>();
            
            // Java runtime information
            systemInfo.put("java_version", System.getProperty("java.version"));
            systemInfo.put("java_vendor", System.getProperty("java.vendor"));
            systemInfo.put("java_runtime", System.getProperty("java.runtime.name"));
            
            // Operating system information
            systemInfo.put("os_name", System.getProperty("os.name"));
            systemInfo.put("os_version", System.getProperty("os.version"));
            systemInfo.put("os_arch", System.getProperty("os.arch"));
            
            // Application information
            systemInfo.put("spring_profiles", System.getProperty("spring.profiles.active", "default"));
            systemInfo.put("timezone", System.getProperty("user.timezone"));
            systemInfo.put("file_encoding", System.getProperty("file.encoding"));
            
            builder.withDetail("system", systemInfo);
        };
    }
}
