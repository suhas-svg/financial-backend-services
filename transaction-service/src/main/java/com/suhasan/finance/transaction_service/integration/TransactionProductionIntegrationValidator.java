package com.suhasan.finance.transaction_service.integration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class TransactionProductionIntegrationValidator {
    private final Environment environment;
    private final GuardrailRiskProvider risk;
    private final GovernedFxRateProvider fx;

    @PostConstruct
    void validate() {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("production")) return;
        for (String key : new String[] {
                "integration.risk.contract-id", "integration.risk.policy-version",
                "integration.risk.authentication-reference", "integration.fx.contract-id",
                "integration.fx.authentication-reference", "integration.fx.reconciliation-reference",
                "integration.consent.approved-version", "integration.consent.jurisdictions",
                "integration.consent.retention-policy", "integration.consent.accessibility-reference",
                "integration.iam.issuer", "integration.iam.audience", "integration.iam.role-mappings",
                "integration.iam.access-review-reference", "integration.iam.revocation-feed"}) require(key);
        if (!risk.health().configured() || !risk.health().healthy()
                || risk.health().provider().startsWith("local")) {
            throw new IllegalStateException("Production guardrail risk provider must be externally configured and healthy");
        }
        if (!fx.health().configured() || !fx.health().healthy()
                || fx.health().mode().startsWith("local")) {
            throw new IllegalStateException("Production licensed FX provider must be externally configured and healthy");
        }
    }

    private void require(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException("Production integration configuration is required: " + key);
        }
    }
}
