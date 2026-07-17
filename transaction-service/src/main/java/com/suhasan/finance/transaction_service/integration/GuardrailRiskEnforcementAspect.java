package com.suhasan.finance.transaction_service.integration;

import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.GuardrailExecutionRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class GuardrailRiskEnforcementAspect {
    private final GuardrailRiskProvider provider;

    @Before("execution(* com.suhasan.finance.transaction_service.outcome.service.OutcomeGuardrailService.execute(..))"
            + " && args(guardrailId,request,userId,idempotencyKey)")
    public void enforce(String guardrailId, GuardrailExecutionRequest request, String userId, String idempotencyKey) {
        var decision = provider.evaluate(new GuardrailRiskProvider.Request(
                userId, guardrailId, "resolved-by-existing-policy", "resolved-by-existing-policy",
                request == null ? null : request.amount(), "resolved-by-existing-policy",
                idempotencyKey == null ? UUID.randomUUID().toString() : idempotencyKey));
        if (decision.outcome() == GuardrailRiskProvider.Outcome.DENY
                || decision.outcome() == GuardrailRiskProvider.Outcome.INDETERMINATE) {
            throw new IllegalStateException("Guardrail risk provider failed closed: " + decision.reason());
        }
        // STEP_UP is additive. The existing TransferAuthorizationService remains
        // authoritative and cannot be bypassed by an ALLOW result.
    }
}
