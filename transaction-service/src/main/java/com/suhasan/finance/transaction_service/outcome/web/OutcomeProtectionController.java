package com.suhasan.finance.transaction_service.outcome.web;

import com.suhasan.finance.transaction_service.outcome.service.OutcomeProtectionService;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/outcome-protection")
@RequiredArgsConstructor
public class OutcomeProtectionController {
    private final OutcomeProtectionService service;
    private final com.suhasan.finance.transaction_service.outcome.service.OutcomeGuardrailService guardrailService;
    private final com.suhasan.finance.transaction_service.outcome.service.OutcomeGuardrailControlService guardrailControlService;

    @PostMapping("/scenarios")
    public ResponseEntity<ScenarioResponse> create(@Valid @RequestBody ScenarioRequest request,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request, currentUser(authentication), idempotencyKey));
    }

    @GetMapping("/scenarios")
    public List<ScenarioSummary> list(Authentication authentication) {
        return service.list(currentUser(authentication));
    }

    @GetMapping("/scenarios/{scenarioId}")
    public ScenarioResponse get(@PathVariable String scenarioId, Authentication authentication) {
        return service.get(scenarioId, currentUser(authentication));
    }

    @PostMapping("/scenarios/{scenarioId}/versions")
    public ResponseEntity<ScenarioResponse> createVersion(@PathVariable String scenarioId,
                                                           @Valid @RequestBody ScenarioRequest request,
                                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                           Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createVersion(scenarioId, request, currentUser(authentication), idempotencyKey));
    }

    @PostMapping("/scenarios/{scenarioId}/refresh")
    public DivergenceResponse refresh(@PathVariable String scenarioId, Authentication authentication) {
        return service.refresh(scenarioId, currentUser(authentication));
    }

    @PostMapping("/guardrails/{guardrailId}/accept")
    public GuardrailResponse acceptGuardrail(@PathVariable String guardrailId,
                                             @Valid @RequestBody GuardrailAcceptRequest request,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             Authentication authentication) {
        return service.acceptGuardrail(guardrailId, request, currentUser(authentication), idempotencyKey);
    }

    @PostMapping("/repairs/{guardrailId}/select")
    public GuardrailResponse selectRepairDraft(@PathVariable String guardrailId,
                                               @Valid @RequestBody RepairDraftSelectRequest request,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               Authentication authentication) {
        return service.selectRepairDraft(guardrailId, request, currentUser(authentication), idempotencyKey);
    }
    @GetMapping("/guardrails/terms")
    public GuardrailTermsResponse guardrailTerms() {
        return guardrailService.terms();
    }

    @GetMapping("/guardrails/runtime-control")
    public GuardrailControlResponse guardrailControl() {
        return guardrailControlService.current();
    }

    @GetMapping("/guardrails/{guardrailId}/policy")
    public GuardrailPolicyResponse guardrailPolicy(@PathVariable String guardrailId,
                                                   Authentication authentication) {
        return guardrailService.policy(guardrailId, currentUser(authentication));
    }

    @GetMapping("/guardrails/{guardrailId}/events")
    public List<GuardrailAuditEventResponse> guardrailEvents(@PathVariable String guardrailId,
                                                             Authentication authentication) {
        return guardrailService.events(guardrailId, currentUser(authentication));
    }

    @PostMapping("/guardrails/{guardrailId}/consent")
    public GuardrailPolicyResponse consentGuardrail(@PathVariable String guardrailId,
                                                    @Valid @RequestBody GuardrailConsentRequest request,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                    Authentication authentication) {
        return guardrailService.consent(guardrailId, request, currentUser(authentication), idempotencyKey);
    }

    @PostMapping("/guardrails/{guardrailId}/activate")
    public GuardrailPolicyResponse activateGuardrail(@PathVariable String guardrailId,
                                                     @Valid @RequestBody GuardrailActivationRequest request,
                                                     Authentication authentication) {
        return guardrailService.activate(guardrailId, request, currentUser(authentication));
    }

    @PostMapping("/guardrails/{guardrailId}/suspend")
    public GuardrailPolicyResponse suspendGuardrail(@PathVariable String guardrailId,
                                                    @Valid @RequestBody GuardrailLifecycleRequest request,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                    Authentication authentication) {
        return guardrailService.suspend(guardrailId, request, currentUser(authentication), idempotencyKey);
    }

    @PostMapping("/guardrails/{guardrailId}/resume")
    public GuardrailPolicyResponse resumeGuardrail(@PathVariable String guardrailId,
                                                   @Valid @RequestBody GuardrailLifecycleRequest request,
                                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   Authentication authentication) {
        return guardrailService.resume(guardrailId, request, currentUser(authentication), idempotencyKey);
    }

    @PostMapping("/guardrails/{guardrailId}/revoke")
    public GuardrailPolicyResponse revokeGuardrail(@PathVariable String guardrailId,
                                                   @Valid @RequestBody GuardrailLifecycleRequest request,
                                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   Authentication authentication) {
        return guardrailService.revoke(guardrailId, request, currentUser(authentication), idempotencyKey);
    }

    @PostMapping("/guardrails/{guardrailId}/executions")
    public GuardrailExecutionResponse executeGuardrail(@PathVariable String guardrailId,
                                                       @Valid @RequestBody GuardrailExecutionRequest request,
                                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                       Authentication authentication) {
        return guardrailService.execute(guardrailId, request, currentUser(authentication), idempotencyKey);
    }

    @PostMapping("/guardrail-executions/{executionId}/authorize")
    public GuardrailExecutionResponse authorizeGuardrailExecution(
            @PathVariable String executionId,
            @Valid @RequestBody GuardrailExecutionAuthorizationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        return guardrailService.authorize(executionId, request, currentUser(authentication), idempotencyKey);
    }

    @DeleteMapping("/guardrail-executions/{executionId}")
    public GuardrailExecutionResponse cancelGuardrailExecution(
            @PathVariable String executionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        return guardrailService.cancelExecution(executionId, currentUser(authentication), idempotencyKey);
    }

    @PostMapping("/warnings/{eventId}/acknowledge")
    public WarningAcknowledgementResponse acknowledgeWarning(@PathVariable String eventId,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                    Authentication authentication) {
        return service.acknowledgeWarning(eventId, currentUser(authentication), idempotencyKey);
    }

    private String currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        return authentication.getName();
    }
}
