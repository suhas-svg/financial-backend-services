package com.suhasan.finance.transaction_service.config;

import io.micrometer.core.instrument.config.MeterFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for metrics and monitoring
 */
@Configuration
@Slf4j
public class MetricsConfig {
    
    /**
     * Configure meter registry with common tags and filters
     */
    @Bean
    public MeterFilter meterFilter() {
        return MeterFilter.commonTags(
                java.util.Arrays.asList(
                        io.micrometer.core.instrument.Tag.of("service", "transaction-service"),
                        io.micrometer.core.instrument.Tag.of("version", "1.0.0")
                )
        );
    }
}
