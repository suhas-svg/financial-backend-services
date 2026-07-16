package com.suhasan.finance.transaction_service.outcome.fx;

import java.time.Instant;

public interface FxRateProvider {
    FxRateQuote quote(String quoteCurrency, String baseCurrency, Instant evaluationTime);
}
