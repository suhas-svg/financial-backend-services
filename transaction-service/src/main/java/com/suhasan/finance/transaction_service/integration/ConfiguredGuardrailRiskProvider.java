package com.suhasan.finance.transaction_service.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ConfiguredGuardrailRiskProvider implements GuardrailRiskProvider {
    private final String provider;
    private final String policyVersion;
    private final String mode;

    public ConfiguredGuardrailRiskProvider(
            @Value("${integration.risk.provider:local-deterministic}") String provider,
            @Value("${integration.risk.policy-version:local-v1}") String policyVersion,
            @Value("${integration.risk.local-mode:ALLOW}") String mode) {
        this.provider = provider == null ? "unconfigured" : provider.trim();
        this.policyVersion = policyVersion == null ? "" : policyVersion.trim();
        this.mode = mode == null ? "INDETERMINATE" : mode.trim().toUpperCase();
    }

    @Override
    public Decision evaluate(Request request) {
        Outcome outcome;
        try { outcome = Outcome.valueOf(mode); }
        catch (IllegalArgumentException ignored) { outcome = Outcome.INDETERMINATE; }
        return new Decision(outcome, provider, policyVersion,
                provider.startsWith("local") ? "Explicit non-production deterministic adapter" : "Provider boundary has no configured client",
                Instant.now(), request.correlationId());
    }

    @Override
    public Health health() {
        boolean local = provider.startsWith("local");
        return new Health(provider, local, local, local ? "NON_PRODUCTION_ADAPTER" : "FAIL_CLOSED",
                policyVersion, Instant.now());
    }
}
