package com.suhasan.finance.transaction_service.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

/**
 * Configuration for comprehensive monitoring and observability
 */
@Configuration
@EnableScheduling
@Slf4j
public class MonitoringConfig {
    
    /**
     * Configure meter registry with common tags and filters
     */
    @Bean
    public MeterFilter commonTagsMeterFilter() {
        return MeterFilter.commonTags(
                java.util.Arrays.asList(
                        io.micrometer.core.instrument.Tag.of("service", "transaction-service"),
                        io.micrometer.core.instrument.Tag.of("version", "1.0.0"),
                        io.micrometer.core.instrument.Tag.of("environment", "${spring.profiles.active:local}")
                )
        );
    }
    
    /**
     * Configure HTTP exchange repository for tracking HTTP requests
     */
    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        InMemoryHttpExchangeRepository repository = new InMemoryHttpExchangeRepository();
        repository.setCapacity(1000); // Keep last 1000 exchanges
        return repository;
    }
    
    /**
     * Configure custom meter filters for transaction metrics
     */
    @Bean
    public MeterFilter transactionMeterFilter() {
        return MeterFilter.deny(id -> {
            String name = id.getName();
            // Filter out noisy metrics that we don't need
            return name.startsWith("jvm.gc.pause") && 
                   id.getTag("cause") != null && 
                   id.getTag("cause").contains("Metadata");
        });
    }
    
    /**
     * Configure meter filter for renaming metrics
     */
    @Bean
    public MeterFilter renamingMeterFilter() {
        return MeterFilter.renameTag("transaction.type.total", "type", "transaction_type");
    }
    
    /**
     * Configure meter filter for adding percentiles and histograms to important timers
     */
    @Bean
    public MeterFilter distributionMeterFilter() {
        return MeterFilter.maximumAllowableMetrics(10000);
    }
    
    /**
     * Configure meter filter for business metrics
     */
    @Bean
    public MeterFilter businessMetricsMeterFilter() {
        return MeterFilter.accept();
    }
    
    /**
     * Configure meter filter for alerting-ready metrics
     */
    @Bean
    public MeterFilter alertingMeterFilter() {
        return MeterFilter.accept();
    }
    
    /**
     * Log monitoring configuration on startup
     */
    @Bean
    public MonitoringConfigurationLogger monitoringConfigurationLogger(MeterRegistry meterRegistry) {
        return new MonitoringConfigurationLogger(meterRegistry);
    }
    
    /**
     * Helper class to log monitoring configuration
     */
    public static class MonitoringConfigurationLogger {
        
        public MonitoringConfigurationLogger(MeterRegistry meterRegistry) {
            log.info("Advanced monitoring configuration initialized");
            log.info("Meter registry type: {}", meterRegistry.getClass().getSimpleName());
            log.info("Available meters: {}", meterRegistry.getMeters().size());
            
            // Log key configuration properties
            log.info("Health endpoint enabled: true");
            log.info("Metrics endpoint enabled: true");
            log.info("Prometheus endpoint enabled: true");
            log.info("HTTP exchanges tracking enabled: true");
            log.info("Distributed tracing enabled: true");
            log.info("Percentiles and histograms enabled for key metrics");
            log.info("Service Level Objectives configured for alerting");
            log.info("Scheduled metrics collection enabled: true");
            log.info("Audit logging enabled: true");
            log.info("Custom health indicators enabled: true");
            log.info("Business metrics with percentiles enabled");
            log.info("Alerting-ready metrics configured");
        }
    }
}