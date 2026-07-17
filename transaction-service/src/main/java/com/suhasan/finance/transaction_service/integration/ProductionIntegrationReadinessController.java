package com.suhasan.finance.transaction_service.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProductionIntegrationReadinessController {
    private final GuardrailRiskProvider riskProvider;
    private final GovernedFxRateProvider fxProvider;
    private final ConsentGovernanceService consentGovernance;
    private final Environment environment;

    @GetMapping("/api/admin/outcome-protection/integration-readiness")
    public Map<String, Object> readiness(Authentication authentication) {
        requireAdmin(authentication);
        return Map.of(
                "checkedAt", Instant.now(),
                "productionReady", false,
                "riskProvider", riskProvider.health(),
                "fxProvider", fxProvider.health(),
                "consentVersions", consentGovernance.versions(),
                "operatorIam", Map.of(
                        "issuerConfigured", present("integration.iam.issuer"),
                        "audienceConfigured", present("integration.iam.audience"),
                        "explicitRoleMapping", present("integration.iam.role-mappings"),
                        "accessReviewConfigured", present("integration.iam.access-review-reference"),
                        "revocationConfigured", present("integration.iam.revocation-feed")),
                "externalApprovalRequired", true);
    }

    @GetMapping("/api/outcome-protection/consent-governance")
    public Map<String, Object> customerEvidence(Authentication authentication) {
        return Map.of("versions", consentGovernance.versions(),
                "evidence", consentGovernance.evidence(current(authentication)),
                "legalApprovalClaimed", false);
    }

    @PostMapping("/api/outcome-protection/consent-governance/withdraw")
    public Map<String, Object> withdraw(@RequestBody ConsentGovernanceService.GovernanceRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey, Authentication authentication) {
        return consentGovernance.record(current(authentication), "WITHDRAWN", request, idempotencyKey);
    }

    @PostMapping("/api/outcome-protection/consent-governance/complaints")
    public Map<String, Object> complaint(@RequestBody ConsentGovernanceService.GovernanceRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey, Authentication authentication) {
        return consentGovernance.record(current(authentication), "COMPLAINT_RECORDED", request, idempotencyKey);
    }

    @PostMapping("/api/outcome-protection/consent-governance/export")
    public Map<String, Object> export(@RequestBody ConsentGovernanceService.GovernanceRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey, Authentication authentication) {
        return consentGovernance.record(current(authentication), "EVIDENCE_EXPORTED", request, idempotencyKey);
    }

    private boolean present(String key) {
        String value = environment.getProperty(key);
        return value != null && !value.isBlank() && !value.startsWith("${");
    }
    private String current(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return authentication.getName();
    }
    private void requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("ROLE_ADMIN is required");
        }
    }
}
