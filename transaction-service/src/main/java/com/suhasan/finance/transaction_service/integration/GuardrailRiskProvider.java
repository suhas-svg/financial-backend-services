package com.suhasan.finance.transaction_service.integration;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Advisory/enforcement input only. ALLOW never bypasses the existing transfer
 * authorization policy; STEP_UP may add verification and DENY blocks before it.
 */
public interface GuardrailRiskProvider {
    Decision evaluate(Request request);
    Health health();

    record Request(String userId, String guardrailId, String fundingAccountId,
                   String protectedAccountId, BigDecimal amount, String currency,
                   String correlationId) {}
    record Decision(Outcome outcome, String provider, String policyVersion, String reason,
                    Instant asOf, String providerReference) {}
    record Health(String provider, boolean configured, boolean healthy, String classification,
                  String policyVersion, Instant checkedAt) {}
    enum Outcome { ALLOW, STEP_UP, DENY, INDETERMINATE }
}
