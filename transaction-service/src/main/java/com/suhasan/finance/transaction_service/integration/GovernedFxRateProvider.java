package com.suhasan.finance.transaction_service.integration;

import com.suhasan.finance.transaction_service.outcome.fx.ConfiguredFxRateProvider;
import com.suhasan.finance.transaction_service.outcome.fx.FxRateProvider;
import com.suhasan.finance.transaction_service.outcome.fx.FxRateQuote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Primary
@Component
public class GovernedFxRateProvider implements FxRateProvider {
    private final ConfiguredFxRateProvider local;
    private final String mode;
    private final String contractId;
    private final String authenticationReference;
    private final String reconciliationReference;

    public GovernedFxRateProvider(ConfiguredFxRateProvider local,
            @Value("${integration.fx.provider-mode:local-configured}") String mode,
            @Value("${integration.fx.contract-id:local-development}") String contractId,
            @Value("${integration.fx.authentication-reference:local-none}") String authenticationReference,
            @Value("${integration.fx.reconciliation-reference:local-static}") String reconciliationReference) {
        this.local = local;
        this.mode = mode == null ? "unconfigured" : mode.trim();
        this.contractId = contractId == null ? "" : contractId.trim();
        this.authenticationReference = authenticationReference == null ? "" : authenticationReference.trim();
        this.reconciliationReference = reconciliationReference == null ? "" : reconciliationReference.trim();
    }

    @Override
    public FxRateQuote quote(String quoteCurrency, String baseCurrency, Instant evaluationTime) {
        if (!mode.equals("local-configured")) {
            throw new IllegalStateException("Licensed FX client is not configured; forecast fails closed");
        }
        if (contractId.isBlank() || authenticationReference.isBlank() || reconciliationReference.isBlank()) {
            throw new IllegalStateException("FX contract, authentication, and reconciliation references are required");
        }
        return local.quote(quoteCurrency, baseCurrency, evaluationTime);
    }

    public FxHealth health() {
        boolean localMode = mode.equals("local-configured");
        return new FxHealth(mode, localMode, localMode, contractId, authenticationReference,
                reconciliationReference, false, Instant.now());
    }

    public record FxHealth(String mode, boolean configured, boolean healthy, String contractId,
                           String authenticationReference, String reconciliationReference,
                           boolean executableFx, Instant checkedAt) {}
}
