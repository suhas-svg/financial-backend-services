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

    @PostMapping("/warnings/{eventId}/acknowledge")
    public ResponseEntity<Void> acknowledgeWarning(@PathVariable String eventId,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                    Authentication authentication) {
        service.acknowledgeWarning(eventId, currentUser(authentication), idempotencyKey);
        return ResponseEntity.noContent().build();
    }

    private String currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        return authentication.getName();
    }
}
