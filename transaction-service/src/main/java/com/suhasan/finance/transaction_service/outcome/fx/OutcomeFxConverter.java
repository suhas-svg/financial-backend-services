package com.suhasan.finance.transaction_service.outcome.fx;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OutcomeFxConverter {
    private static final MathContext CONVERSION_CONTEXT = new MathContext(34, RoundingMode.HALF_EVEN);
    private final FxRateProvider rateProvider;

    public Conversion convert(BigDecimal amount, String quoteCurrency, String baseCurrency, Instant evaluationTime) {
        FxRateQuote quote = rateProvider.quote(quoteCurrency, baseCurrency, evaluationTime);
        BigDecimal converted = amount.multiply(quote.rate(), CONVERSION_CONTEXT).setScale(2, RoundingMode.HALF_EVEN);
        return new Conversion(amount.setScale(2, RoundingMode.HALF_EVEN), quoteCurrency, converted, baseCurrency, quote);
    }

    public record Conversion(BigDecimal sourceAmount, String sourceCurrency,
                             BigDecimal convertedAmount, String baseCurrency, FxRateQuote quote) {}
}
