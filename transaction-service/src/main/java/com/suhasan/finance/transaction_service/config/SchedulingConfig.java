package com.suhasan.finance.transaction_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runs the @Scheduled background jobs. On unless app.scheduling.enabled=false, which only
 * tests that start the web layer without a real database (OpenApiSpecTest) should set.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
