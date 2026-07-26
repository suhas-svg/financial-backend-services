package com.suhasan.finance.account_service.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/integration-readiness")
@RequiredArgsConstructor
public class AccountIntegrationReadinessController {
    private final NotificationProvider notificationProvider;
    private final NotificationProviderReceiptRepository receipts;
    private final Environment environment;

    @GetMapping
    public Map<String, Object> readiness(final Authentication authentication) {
        requireAdmin(authentication);
        final var health = notificationProvider.health();
        return Map.of(
                "checkedAt", Instant.now(),
                "notificationProvider", health,
                "notificationAccepted", receipts.countByClassification("ACCEPTED"),
                "notificationUnreconciled", receipts.countByReconciliationStatus("UNRECONCILED"),
                "iam", Map.of(
                        "issuerConfigured", present("integration.iam.issuer"),
                        "audienceConfigured", present("integration.iam.audience"),
                        "explicitRoleMapping", present("integration.iam.role-mappings"),
                        "accessReviewConfigured", present("integration.iam.access-review-reference"),
                        "revocationConfigured", present("integration.iam.revocation-feed")),
                "mfaKms", Map.of(
                        "provider", environment.getProperty("integration.mfa.kms.provider", "local-key"),
                        "activeKeyId", environment.getProperty("integration.mfa.kms.active-key-id", "legacy"),
                        "healthEvidenceConfigured", present("integration.mfa.kms.health-reference"),
                        "secretMaterialExposed", false));
    }

    private boolean present(final String key) {
        final String value = environment.getProperty(key);
        return value != null && !value.isBlank() && !value.startsWith("${");
    }

    private void requireAdmin(final Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("ROLE_ADMIN is required");
        }
    }
}
