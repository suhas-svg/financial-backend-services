package com.suhasan.finance.transaction_service.integration;

import com.suhasan.finance.transaction_service.outcome.fx.ConfiguredFxRateProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernedFxRateProviderTest {
    private final ConfiguredFxRateProvider configured = new ConfiguredFxRateProvider(
            "USD/INR=83.25", "2026-07-17T00:00:00Z", "TEST_FEED",
            "licensed-test-fixture", 86400, "REJECT");

    @Test
    void nonLocalModeWithoutImplementedLicensedClientFailsClosed() {
        var provider = new GovernedFxRateProvider(configured, "licensed-http",
                "contract-1", "secret-manager:key", "reconciliation-feed");

        assertThatThrownBy(() -> provider.quote("USD", "INR", Instant.parse("2026-07-17T01:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fails closed");
        assertThat(provider.health().executableFx()).isFalse();
    }

    @Test
    void explicitLocalAdapterPreservesQuoteProvenanceAndForecastOnlyBoundary() {
        var provider = new GovernedFxRateProvider(configured, "local-configured",
                "local-development", "local-none", "local-static");
        var quote = provider.quote("USD", "INR", Instant.parse("2026-07-17T01:00:00Z"));

        assertThat(quote.provider()).isEqualTo("TEST_FEED");
        assertThat(quote.provenance()).isEqualTo("licensed-test-fixture");
        assertThat(provider.health().executableFx()).isFalse();
    }
}
