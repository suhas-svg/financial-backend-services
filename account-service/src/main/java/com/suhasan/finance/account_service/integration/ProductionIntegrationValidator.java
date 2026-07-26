package com.suhasan.finance.account_service.integration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class ProductionIntegrationValidator {
    private final Environment environment;
    private final NotificationProvider notificationProvider;

    @PostConstruct
    void validate() {
        final boolean production = Arrays.asList(environment.getActiveProfiles()).contains("production");
        if (!production) return;
        require("integration.notification.contract-id");
        require("integration.iam.issuer");
        require("integration.iam.audience");
        require("integration.iam.role-mappings");
        require("integration.iam.access-review-reference");
        require("integration.iam.revocation-feed");
        require("integration.mfa.kms.provider");
        require("integration.mfa.kms.active-key-id");
        require("integration.mfa.kms.health-reference");
        final var health = notificationProvider.health();
        if (!health.configured() || !health.healthy() || health.provider().startsWith("local")) {
            throw new IllegalStateException("Production notification provider must be externally configured and healthy");
        }
    }

    private void require(final String key) {
        final String value = environment.getProperty(key);
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException("Production integration configuration is required: " + key);
        }
    }
}
