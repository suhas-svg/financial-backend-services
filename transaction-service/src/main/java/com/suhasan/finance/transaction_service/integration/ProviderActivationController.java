package com.suhasan.finance.transaction_service.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProviderActivationController {
    private final ProviderActivationService service;

    @GetMapping("/api/admin/provider-activations")
    public List<Map<String, Object>> list(Authentication authentication) {
        requireAdmin(authentication);
        return service.list(true);
    }

    @GetMapping("/api/admin/provider-activations/{activationId}")
    public Map<String, Object> detail(@PathVariable String activationId, Authentication authentication) {
        requireAdmin(authentication);
        return service.detail(activationId);
    }

    @PostMapping("/api/admin/provider-activations")
    public Map<String, Object> create(@RequestBody ProviderActivationService.CreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Operator-Mfa-Evidence") String mfaEvidence,
            @RequestHeader("X-Operator-Mfa-Verified-At") Instant mfaVerifiedAt,
            Authentication authentication) {
        requireAdmin(authentication);
        return service.create(request, operator(authentication, mfaEvidence, mfaVerifiedAt), idempotencyKey);
    }

    @PostMapping("/api/admin/provider-activations/{activationId}/certify")
    public Map<String, Object> certify(@PathVariable String activationId,
            @RequestBody ProviderActivationService.CertificationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Operator-Mfa-Evidence") String mfaEvidence,
            @RequestHeader("X-Operator-Mfa-Verified-At") Instant mfaVerifiedAt,
            Authentication authentication) {
        requireAdmin(authentication);
        return service.certify(activationId, request,
                operator(authentication, mfaEvidence, mfaVerifiedAt), idempotencyKey);
    }

    @PostMapping("/api/admin/provider-activations/{activationId}/approve")
    public Map<String, Object> approve(@PathVariable String activationId,
            @RequestBody ProviderActivationService.ApprovalRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Operator-Mfa-Evidence") String mfaEvidence,
            @RequestHeader("X-Operator-Mfa-Verified-At") Instant mfaVerifiedAt,
            Authentication authentication) {
        requireAdmin(authentication);
        return service.approve(activationId, request,
                operator(authentication, mfaEvidence, mfaVerifiedAt), idempotencyKey);
    }

    @PostMapping("/api/admin/provider-activations/{activationId}/activate")
    public Map<String, Object> activate(@PathVariable String activationId,
            @RequestBody ProviderActivationService.ActivationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Operator-Mfa-Evidence") String mfaEvidence,
            @RequestHeader("X-Operator-Mfa-Verified-At") Instant mfaVerifiedAt,
            Authentication authentication) {
        requireAdmin(authentication);
        return service.activate(activationId, request,
                operator(authentication, mfaEvidence, mfaVerifiedAt), idempotencyKey);
    }

    @PostMapping("/api/admin/provider-activations/{activationId}/suspend")
    public Map<String, Object> suspend(@PathVariable String activationId,
            @RequestBody ProviderActivationService.SuspensionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Operator-Mfa-Evidence") String mfaEvidence,
            @RequestHeader("X-Operator-Mfa-Verified-At") Instant mfaVerifiedAt,
            Authentication authentication) {
        requireAdmin(authentication);
        return service.suspend(activationId, request,
                operator(authentication, mfaEvidence, mfaVerifiedAt), idempotencyKey);
    }

    @GetMapping("/api/outcome-protection/provider-activation-status")
    public Map<String, Object> customerStatus(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return Map.of(
                "states", service.list(false),
                "productionApprovalClaimed", false,
                "executableFx", false,
                "autonomousMoneyMovement", false);
    }

    private ProviderActivationService.OperatorContext operator(Authentication authentication,
            String mfaEvidence, Instant mfaVerifiedAt) {
        return new ProviderActivationService.OperatorContext(authentication.getName(), mfaEvidence, mfaVerifiedAt);
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("ROLE_ADMIN is required");
        }
    }
}
