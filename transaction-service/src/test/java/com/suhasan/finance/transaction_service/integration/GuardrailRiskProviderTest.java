package com.suhasan.finance.transaction_service.integration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GuardrailRiskProviderTest {
    @Test
    void explicitLocalAdapterIsVisibleAndDoesNotClaimProductionReadiness() {
        var provider = new ConfiguredGuardrailRiskProvider("local-deterministic", "local-v1", "ALLOW");
        var decision = provider.evaluate(new GuardrailRiskProvider.Request(
                "customer", "guardrail", "funding", "protected", BigDecimal.TEN, "USD", "correlation"));

        assertThat(decision.outcome()).isEqualTo(GuardrailRiskProvider.Outcome.ALLOW);
        assertThat(provider.health().classification()).isEqualTo("NON_PRODUCTION_ADAPTER");
    }

    @Test
    void unknownProviderModeIsIndeterminateAndThereforeFailClosed() {
        var provider = new ConfiguredGuardrailRiskProvider("provider", "policy-v1", "unexpected");
        assertThat(provider.evaluate(new GuardrailRiskProvider.Request(
                "customer", "guardrail", "funding", "protected", BigDecimal.TEN, "USD", "correlation")).outcome())
                .isEqualTo(GuardrailRiskProvider.Outcome.INDETERMINATE);
    }
}
