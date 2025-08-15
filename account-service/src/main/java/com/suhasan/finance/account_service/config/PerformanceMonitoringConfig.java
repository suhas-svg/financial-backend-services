package com.suhasan.finance.account_service.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Configuration for performance monitoring and deployment-related metrics
 */
@Configuration
public class PerformanceMonitoringConfig {

    private final AtomicLong lastDeploymentTime = new AtomicLong(Instant.now().toEpochMilli());
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /**
     * Performance monitoring filter to track request metrics
     */
    @Bean
    public OncePerRequestFilter performanceMonitoringFilter(MeterRegistry meterRegistry) {
        Timer requestTimer = Timer.builder("http_request_duration")
                .description("HTTP request duration")
                .register(meterRegistry);
                
        Counter requestCounter = Counter.builder("http_requests_total")
                .description("Total HTTP requests")
                .register(meterRegistry);
                
        Counter errorCounter = Counter.builder("http_errors_total")
                .description("Total HTTP errors")
                .register(meterRegistry);

        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, 
                                          HttpServletResponse response, 
                                          FilterChain filterChain) throws ServletException, IOException {
                
                Timer.Sample sample = Timer.start(meterRegistry);
                
                try {
                    filterChain.doFilter(request, response);
                    
                    // Increment request counter
                    requestCounter.increment();
                    requestCount.incrementAndGet();
                    
                    // Track errors
                    if (response.getStatus() >= 400) {
                        errorCounter.increment();
                        errorCount.incrementAndGet();
                    }
                    
                } finally {
                    sample.stop(requestTimer);
                }
            }
        };
    }

    /**
     * Deployment performance metrics
     */
    @Bean
    public Timer deploymentDurationTimer(MeterRegistry meterRegistry) {
        return Timer.builder("deployment_duration_seconds")
                .description("Time taken for deployment operations")
                .register(meterRegistry);
    }

    @Bean
    public Counter deploymentEventCounter(MeterRegistry meterRegistry) {
        return Counter.builder("deployment_events_total")
                .description("Total deployment events")
                .register(meterRegistry);
    }

    @Bean
    public Gauge deploymentTimeSinceLastGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("deployment_time_since_last_seconds", this, PerformanceMonitoringConfig::getTimeSinceLastDeployment)
                .description("Time since last deployment in seconds")
                .register(meterRegistry);
    }

    /**
     * Application performance metrics
     */
    @Bean
    public Gauge applicationRequestRateGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("application_request_rate", this, PerformanceMonitoringConfig::getCurrentRequestRate)
                .description("Current application request rate")
                .register(meterRegistry);
    }

    @Bean
    public Gauge applicationErrorRateGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("application_error_rate", this, PerformanceMonitoringConfig::getCurrentErrorRate)
                .description("Current application error rate")
                .register(meterRegistry);
    }

    /**
     * Post-deployment performance tracking
     */
    @Bean
    public Counter postDeploymentHealthCheckCounter(MeterRegistry meterRegistry) {
        return Counter.builder("post_deployment_health_checks_total")
                .description("Total post-deployment health checks performed")
                .register(meterRegistry);
    }

    @Bean
    public Timer postDeploymentHealthCheckTimer(MeterRegistry meterRegistry) {
        return Timer.builder("post_deployment_health_check_duration")
                .description("Time taken for post-deployment health checks")
                .register(meterRegistry);
    }

    @Bean
    public Counter deploymentRollbackCounter(MeterRegistry meterRegistry) {
        return Counter.builder("deployment_rollbacks_total")
                .description("Total deployment rollbacks performed")
                .register(meterRegistry);
    }

    /**
     * Performance regression detection metrics
     */
    @Bean
    public Gauge performanceRegressionScoreGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("performance_regression_score", this, PerformanceMonitoringConfig::calculatePerformanceRegressionScore)
                .description("Performance regression score (0-100, higher is better)")
                .register(meterRegistry);
    }

    // Helper methods for gauge calculations
    private double getTimeSinceLastDeployment() {
        return (Instant.now().toEpochMilli() - lastDeploymentTime.get()) / 1000.0;
    }

    private double getCurrentRequestRate() {
        // This would typically calculate requests per second over a time window
        // For now, return the total request count
        return requestCount.get();
    }

    private double getCurrentErrorRate() {
        long totalRequests = requestCount.get();
        long totalErrors = errorCount.get();
        
        if (totalRequests == 0) {
            return 0.0;
        }
        
        return (double) totalErrors / totalRequests * 100.0;
    }

    private double calculatePerformanceRegressionScore() {
        // This would typically compare current performance metrics with baseline
        // For now, return a score based on error rate
        double errorRate = getCurrentErrorRate();
        
        if (errorRate == 0.0) {
            return 100.0;
        } else if (errorRate < 1.0) {
            return 90.0;
        } else if (errorRate < 5.0) {
            return 70.0;
        } else {
            return 50.0;
        }
    }

    /**
     * Update deployment timestamp (called when deployment occurs)
     */
    public void updateDeploymentTime() {
        lastDeploymentTime.set(Instant.now().toEpochMilli());
    }

    /**
     * Reset performance counters (useful for testing)
     */
    public void resetCounters() {
        requestCount.set(0);
        errorCount.set(0);
    }
}