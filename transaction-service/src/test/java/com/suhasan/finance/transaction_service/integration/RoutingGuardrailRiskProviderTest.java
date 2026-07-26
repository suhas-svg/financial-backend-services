package com.suhasan.finance.transaction_service.integration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingGuardrailRiskProviderTest {
    private final GuardrailRiskProvider.Request request = new GuardrailRiskProvider.Request(
            "user", "guardrail", "funding", "protected", BigDecimal.TEN, "USD", "correlation");

    @Test
    void delegatesDeterministicLocalMode() {
        ConfiguredGuardrailRiskProvider local = mock(ConfiguredGuardrailRiskProvider.class);
        GuardrailRiskProvider.Decision decision = new GuardrailRiskProvider.Decision(
                GuardrailRiskProvider.Outcome.ALLOW, "local", "v1", "ok", Instant.now(), null);
        GuardrailRiskProvider.Health health = new GuardrailRiskProvider.Health(
                "local", true, true, "HEALTHY", "v1", Instant.now());
        when(local.evaluate(request)).thenReturn(decision);
        when(local.health()).thenReturn(health);
        RoutingGuardrailRiskProvider provider = new RoutingGuardrailRiskProvider(
                local, "local-deterministic", "v1", "contract", "", "");

        assertThat(provider.evaluate(request)).isSameAs(decision);
        assertThat(provider.health()).isSameAs(health);
        verify(local).evaluate(request);
        verify(local).health();
    }

    @Test
    void failsClosedWhenModeOrCredentialsAreUnavailable() {
        ConfiguredGuardrailRiskProvider local = mock(ConfiguredGuardrailRiskProvider.class);
        RoutingGuardrailRiskProvider provider = new RoutingGuardrailRiskProvider(
                local, "http", "v2", "contract", "", "");
        assertThat(provider.evaluate(request).outcome()).isEqualTo(GuardrailRiskProvider.Outcome.INDETERMINATE);
        assertThat(provider.health().configured()).isFalse();
        assertThat(provider.health().healthy()).isFalse();

        RoutingGuardrailRiskProvider unconfigured = new RoutingGuardrailRiskProvider(
                local, null, "v2", "contract", null, null);
        assertThat(unconfigured.evaluate(request).outcome()).isEqualTo(GuardrailRiskProvider.Outcome.INDETERMINATE);
        assertThat(unconfigured.health().classification()).isEqualTo("FAIL_CLOSED");
    }
}
