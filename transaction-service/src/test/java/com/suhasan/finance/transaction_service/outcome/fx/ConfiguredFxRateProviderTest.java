package com.suhasan.finance.transaction_service.outcome.fx;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class ConfiguredFxRateProviderTest {
    @Test
    void returnsDecimalSafeQuoteWithProviderAndProvenance() {
        var provider = new ConfiguredFxRateProvider("USD/INR=83.250000000000",
                "2026-07-16T00:00:00Z", "TREASURY_REFERENCE", "approved daily reference", 86400, "REJECT");
        var converter = new OutcomeFxConverter(provider);

        var conversion = converter.convert(new BigDecimal("10.01"), "USD", "INR",
                Instant.parse("2026-07-16T12:00:00Z"));

        assertThat(conversion.convertedAmount()).isEqualByComparingTo("833.33");
        assertThat(conversion.quote().provider()).isEqualTo("TREASURY_REFERENCE");
        assertThat(conversion.quote().provenance()).isEqualTo("approved daily reference");
        assertThat(conversion.quote().stale()).isFalse();
    }

    @Test
    void missingAndStaleRatesFailClosedByDefault() {
        var missing = new ConfiguredFxRateProvider("USD/INR=83.25", "2026-07-16T00:00:00Z",
                "REFERENCE", "approved", 86400, "REJECT");
        assertThatThrownBy(() -> missing.quote("EUR", "INR", Instant.parse("2026-07-16T01:00:00Z")))
                .hasMessageContaining("Missing FX reference rate");
        assertThatThrownBy(() -> missing.quote("USD", "INR", Instant.parse("2026-07-18T00:00:01Z")))
                .hasMessageContaining("is stale");
    }

    @Test
    void warnPolicyRetainsVisibleStalenessEvidence() {
        var provider = new ConfiguredFxRateProvider("USD/INR=83.25", "2026-07-01T00:00:00Z",
                "REFERENCE", "approved", 60, "WARN");
        FxRateQuote quote = provider.quote("USD", "INR", Instant.parse("2026-07-16T00:00:00Z"));
        assertThat(quote.stale()).isTrue();
        assertThat(quote.stalenessPolicy()).isEqualTo("WARN");
    }
}
