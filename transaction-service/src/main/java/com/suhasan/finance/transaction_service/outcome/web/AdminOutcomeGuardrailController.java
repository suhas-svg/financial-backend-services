package com.suhasan.finance.transaction_service.outcome.web;

import com.suhasan.finance.transaction_service.outcome.service.OutcomeGuardrailControlService;
import com.suhasan.finance.transaction_service.outcome.service.OutcomeGuardrailService;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/outcome-protection/guardrails")
@RequiredArgsConstructor
public class AdminOutcomeGuardrailController {
    private final OutcomeGuardrailControlService controlService;
    private final OutcomeGuardrailService guardrailService;

    @GetMapping("/control")
    public GuardrailControlResponse control() {
        return controlService.current();
    }

    @PutMapping("/control")
    public GuardrailControlResponse updateControl(@Valid @RequestBody GuardrailControlUpdateRequest request,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                  Authentication authentication) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        return controlService.update(request, currentOperator(authentication), idempotencyKey);
    }

    @GetMapping("/control/events")
    public List<GuardrailControlEventResponse> controlEvents() {
        return controlService.events();
    }

    @GetMapping
    public List<GuardrailOperatorPolicyResponse> policies() {
        return guardrailService.operatorPolicies();
    }

    private String currentOperator(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Operator authentication is required");
        }
        return authentication.getName();
    }
}
