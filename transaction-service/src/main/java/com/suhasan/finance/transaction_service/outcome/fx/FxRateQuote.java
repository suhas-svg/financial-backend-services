package com.suhasan.finance.transaction_service.outcome.fx;

import java.math.BigDecimal;
import java.time.Instant;

public record FxRateQuote(
        String quoteCurrency,
        String baseCurrency,
        BigDecimal rate,
        Instant asOf,
        String provider,
        String provenance,
        boolean stale,
        String stalenessPolicy) {
}
