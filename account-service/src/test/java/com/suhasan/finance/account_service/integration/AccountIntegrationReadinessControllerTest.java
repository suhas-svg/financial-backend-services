package com.suhasan.finance.account_service.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountIntegrationReadinessControllerTest {
    @Test
    void requiresAdminAuthentication() {
        var controller = new AccountIntegrationReadinessController(
                mock(NotificationProvider.class), mock(NotificationProviderReceiptRepository.class), mock(Environment.class));
        assertThatThrownBy(() -> controller.readiness(null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.readiness(new TestingAuthenticationToken("user", "n/a", "ROLE_USER")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsCountsAndFailClosedConfigurationEvidence() {
        NotificationProvider provider = mock(NotificationProvider.class);
        NotificationProviderReceiptRepository receipts = mock(NotificationProviderReceiptRepository.class);
        Environment environment = mock(Environment.class);
        when(provider.health()).thenReturn(new NotificationProvider.ProviderHealth(
                "external", true, true, "HEALTHY", Instant.now(), "evidence"));
        when(receipts.countByClassification("ACCEPTED")).thenReturn(7L);
        when(receipts.countByReconciliationStatus("UNRECONCILED")).thenReturn(2L);
        when(environment.getProperty("integration.iam.issuer")).thenReturn("issuer");
        when(environment.getProperty("integration.iam.audience")).thenReturn(" ");
        when(environment.getProperty("integration.iam.role-mappings")).thenReturn("${MISSING}");
        when(environment.getProperty("integration.iam.access-review-reference")).thenReturn("review");
        when(environment.getProperty("integration.iam.revocation-feed")).thenReturn("feed");
        when(environment.getProperty("integration.mfa.kms.provider", "local-key")).thenReturn("kms");
        when(environment.getProperty("integration.mfa.kms.active-key-id", "legacy")).thenReturn("v2");
        when(environment.getProperty("integration.mfa.kms.health-reference")).thenReturn(null);

        Map<String, Object> result = new AccountIntegrationReadinessController(provider, receipts, environment)
                .readiness(new TestingAuthenticationToken("admin", "n/a", "ROLE_ADMIN"));

        assertThat(result).containsEntry("notificationAccepted", 7L)
                .containsEntry("notificationUnreconciled", 2L);
        Map<String, Object> iam = (Map<String, Object>) result.get("iam");
        assertThat(iam).containsEntry("issuerConfigured", true)
                .containsEntry("audienceConfigured", false)
                .containsEntry("explicitRoleMapping", false);
        Map<String, Object> kms = (Map<String, Object>) result.get("mfaKms");
        assertThat(kms).containsEntry("provider", "kms")
                .containsEntry("activeKeyId", "v2")
                .containsEntry("healthEvidenceConfigured", false)
                .containsEntry("secretMaterialExposed", false);
    }
}
