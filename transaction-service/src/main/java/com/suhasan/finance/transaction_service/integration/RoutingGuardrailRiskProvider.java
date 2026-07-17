package com.suhasan.finance.transaction_service.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Primary
@Component
public class RoutingGuardrailRiskProvider implements GuardrailRiskProvider {
    private final ConfiguredGuardrailRiskProvider local;
    private final String mode;
    private final String policyVersion;
    private final String contractId;
    private final RestClient client;

    public RoutingGuardrailRiskProvider(ConfiguredGuardrailRiskProvider local,
            @Value("${integration.risk.provider:local-deterministic}") String mode,
            @Value("${integration.risk.policy-version:local-v1}") String policyVersion,
            @Value("${integration.risk.contract-id:local-development}") String contractId,
            @Value("${integration.risk.endpoint:}") String endpoint,
            @Value("${integration.risk.bearer-token:}") String bearerToken) {
        this.local = local;
        this.mode = mode == null ? "unconfigured" : mode.trim();
        this.policyVersion = policyVersion;
        this.contractId = contractId;
        this.client = endpoint == null || endpoint.isBlank() || bearerToken == null || bearerToken.isBlank()
                ? null : RestClient.builder().baseUrl(endpoint.trim())
                .defaultHeader("Authorization", "Bearer " + bearerToken).build();
    }

    @Override
    public Decision evaluate(Request request) {
        if (mode.equals("local-deterministic")) return local.evaluate(request);
        if (!mode.equals("http") || client == null) return indeterminate("Risk provider credentials or endpoint are unavailable");
        try {
            ProviderDecision response = client.post().uri("/v1/guardrail-decisions")
                    .body(Map.of(
                            "subject", request.userId(),
                            "guardrailId", request.guardrailId(),
                            "amount", request.amount(),
                            "correlationId", request.correlationId(),
                            "policyVersion", policyVersion,
                            "contractId", contractId))
                    .retrieve().body(ProviderDecision.class);
            if (response == null || response.outcome() == null || response.asOf() == null) {
                return indeterminate("Risk provider returned an invalid decision");
            }
            Outcome outcome;
            try { outcome = Outcome.valueOf(response.outcome().toUpperCase()); }
            catch (IllegalArgumentException invalid) { return indeterminate("Risk provider returned an unknown outcome"); }
            return new Decision(outcome, "http", policyVersion, safe(response.reason()),
                    response.asOf(), response.reference());
        } catch (ResourceAccessException timeout) {
            return indeterminate("Risk provider timed out");
        } catch (RuntimeException failure) {
            return indeterminate("Risk provider is unavailable");
        }
    }

    @Override
    public Health health() {
        if (mode.equals("local-deterministic")) return local.health();
        if (!mode.equals("http") || client == null) {
            return new Health(mode, false, false, "FAIL_CLOSED", policyVersion, Instant.now());
        }
        try {
            HttpStatusCode status = client.get().uri("/health").retrieve().toBodilessEntity().getStatusCode();
            boolean healthy = status.is2xxSuccessful();
            return new Health("http", true, healthy, healthy ? "HEALTHY" : "UNAVAILABLE",
                    policyVersion, Instant.now());
        } catch (RuntimeException failure) {
            return new Health("http", true, false, "UNAVAILABLE", policyVersion, Instant.now());
        }
    }

    private Decision indeterminate(String reason) {
        return new Decision(Outcome.INDETERMINATE, mode, policyVersion, reason, Instant.now(), null);
    }
    private String safe(String reason) {
        if (reason == null) return "No provider reason supplied";
        String value = reason.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
    private record ProviderDecision(String outcome, String reason, Instant asOf, String reference) {}
}
