package com.suhasan.finance.transaction_service.outcome.fx;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only reference-rate boundary. Rates are forecasting inputs only and can never execute FX.
 * Format: USD/INR=83.250000000000;EUR/INR=90.100000000000.
 */
@Component
public class ConfiguredFxRateProvider implements FxRateProvider {
    private final Map<String, BigDecimal> rates;
    private final Instant asOf;
    private final String provider;
    private final String provenance;
    private final Duration maxAge;
    private final String stalenessPolicy;

    public ConfiguredFxRateProvider(
            @Value("${outcome-protection.fx.rates:}") String configuredRates,
            @Value("${outcome-protection.fx.as-of:}") String configuredAsOf,
            @Value("${outcome-protection.fx.provider:UNCONFIGURED}") String provider,
            @Value("${outcome-protection.fx.provenance:}") String provenance,
            @Value("${outcome-protection.fx.max-age-seconds:86400}") long maxAgeSeconds,
            @Value("${outcome-protection.fx.staleness-policy:REJECT}") String stalenessPolicy) {
        this.rates = parse(configuredRates);
        this.asOf = configuredAsOf == null || configuredAsOf.isBlank() ? null : Instant.parse(configuredAsOf.trim());
        this.provider = provider == null ? "UNCONFIGURED" : provider.trim();
        this.provenance = provenance == null ? "" : provenance.trim();
        this.maxAge = Duration.ofSeconds(Math.max(0, maxAgeSeconds));
        this.stalenessPolicy = stalenessPolicy == null ? "REJECT" : stalenessPolicy.trim().toUpperCase(Locale.ROOT);
        if (!this.stalenessPolicy.equals("REJECT") && !this.stalenessPolicy.equals("WARN")) {
            throw new IllegalStateException("Outcome Protection FX staleness policy must be REJECT or WARN");
        }
    }

    @Override
    public FxRateQuote quote(String quoteCurrency, String baseCurrency, Instant evaluationTime) {
        String quote = normalize(quoteCurrency);
        String base = normalize(baseCurrency);
        if (quote.equals(base)) {
            return new FxRateQuote(quote, base, BigDecimal.ONE, Instant.EPOCH,
                    "IDENTITY", "Same-currency identity rate", false, stalenessPolicy);
        }
        if (asOf == null || provider.isBlank() || provider.equals("UNCONFIGURED") || provenance.isBlank()) {
            throw new IllegalStateException("FX reference-rate provider, as-of time, and provenance must be configured for multi-currency forecasts");
        }
        BigDecimal rate = rates.get(key(quote, base));
        if (rate == null) {
            throw new IllegalStateException("Missing FX reference rate for " + quote + "/" + base);
        }
        boolean stale = asOf.plus(maxAge).isBefore(evaluationTime);
        if (stale && stalenessPolicy.equals("REJECT")) {
            throw new IllegalStateException("FX reference rate for " + quote + "/" + base + " is stale as of " + asOf);
        }
        return new FxRateQuote(quote, base, rate, asOf, provider, provenance, stale, stalenessPolicy);
    }

    private Map<String, BigDecimal> parse(String configured) {
        Map<String, BigDecimal> parsed = new LinkedHashMap<>();
        if (configured == null || configured.isBlank()) return parsed;
        for (String entry : configured.split(";")) {
            String[] pair = entry.trim().split("=", 2);
            if (pair.length != 2 || !pair[0].matches("[A-Za-z]{3}/[A-Za-z]{3}")) {
                throw new IllegalStateException("Outcome Protection FX rates must use QUOTE/BASE=RATE entries");
            }
            BigDecimal rate = new BigDecimal(pair[1].trim());
            if (rate.signum() <= 0) throw new IllegalStateException("Outcome Protection FX rates must be positive");
            parsed.put(pair[0].toUpperCase(Locale.ROOT), rate);
        }
        return Map.copyOf(parsed);
    }

    private String normalize(String currency) {
        if (currency == null || !currency.matches("[A-Za-z]{3}")) throw new IllegalArgumentException("Currency must be a three-letter code");
        return currency.toUpperCase(Locale.ROOT);
    }

    private String key(String quote, String base) { return quote + "/" + base; }
}
