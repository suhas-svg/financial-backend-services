package com.suhasan.finance.transaction_service.integration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionIntegrationReadinessTest {
    @Test
    void validatorSkipsNonProductionAndFailsClosedForMissingProductionEvidence() {
        var environment = new MockEnvironment();
        var validator = new TransactionProductionIntegrationValidator(
                environment, mock(GuardrailRiskProvider.class), mock(GovernedFxRateProvider.class));
        validator.validate();

        environment.setActiveProfiles("production");
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration is required");
    }

    @Test
    void validatorAcceptsOnlyHealthyExternalProviders() {
        var environment = productionEnvironment();
        GuardrailRiskProvider risk = mock(GuardrailRiskProvider.class);
        GovernedFxRateProvider fx = mock(GovernedFxRateProvider.class);
        when(risk.health()).thenReturn(new GuardrailRiskProvider.Health(
                "external-risk", true, true, "BOUNDARY_CONFIGURED", "v1", Instant.now()));
        when(fx.health()).thenReturn(new GovernedFxRateProvider.FxHealth(
                "external-fx", true, true, "contract", "auth", "recon", false, Instant.now()));

        new TransactionProductionIntegrationValidator(environment, risk, fx).validate();

        when(risk.health()).thenReturn(new GuardrailRiskProvider.Health(
                "local-risk", true, true, "LOCAL", "v1", Instant.now()));
        assertThatThrownBy(() -> new TransactionProductionIntegrationValidator(environment, risk, fx).validate())
                .hasMessageContaining("risk provider");
    }

    @Test
    void readinessRequiresAdminAndKeepsExternalApprovalFalseClaimClosed() {
        GuardrailRiskProvider risk = mock(GuardrailRiskProvider.class);
        GovernedFxRateProvider fx = mock(GovernedFxRateProvider.class);
        ConsentGovernanceService consent = mock(ConsentGovernanceService.class);
        when(risk.health()).thenReturn(new GuardrailRiskProvider.Health(
                "local", true, true, "NON_PRODUCTION", "v1", Instant.now()));
        when(fx.health()).thenReturn(new GovernedFxRateProvider.FxHealth(
                "local-configured", true, true, "local", "none", "static", false, Instant.now()));
        when(consent.versions()).thenReturn(List.of(Map.of("version", "v1")));
        var controller = new ProductionIntegrationReadinessController(risk, fx, consent, productionEnvironment());

        assertThatThrownBy(() -> controller.readiness(null)).hasMessageContaining("ROLE_ADMIN");
        var admin = new TestingAuthenticationToken("admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        admin.setAuthenticated(true);
        assertThat(controller.readiness(admin)).containsEntry("productionReady", false)
                .containsEntry("externalApprovalRequired", true);
    }

    @Test
    void customerConsentOperationsUseAuthenticatedIdentity() {
        ConsentGovernanceService consent = mock(ConsentGovernanceService.class);
        when(consent.versions()).thenReturn(List.of());
        when(consent.evidence("user-1")).thenReturn(List.of());
        when(consent.record(anyString(), anyString(), any(), anyString())).thenReturn(Map.of("ok", true));
        var controller = new ProductionIntegrationReadinessController(
                mock(GuardrailRiskProvider.class), mock(GovernedFxRateProvider.class), consent, new MockEnvironment());
        var user = new TestingAuthenticationToken("user-1", "n/a");
        user.setAuthenticated(true);
        var request = new ConsentGovernanceService.GovernanceRequest("p1", "v1", "US", "detail");

        assertThat(controller.customerEvidence(user)).containsEntry("legalApprovalClaimed", false);
        assertThat(controller.withdraw(request, "key-1", user)).containsEntry("ok", true);
        assertThat(controller.complaint(request, "key-2", user)).containsEntry("ok", true);
        assertThat(controller.export(request, "key-3", user)).containsEntry("ok", true);
        assertThatThrownBy(() -> controller.customerEvidence(null)).hasMessageContaining("Authentication");
    }

    private MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        for (String key : List.of(
                "integration.risk.contract-id", "integration.risk.policy-version",
                "integration.risk.authentication-reference", "integration.fx.contract-id",
                "integration.fx.authentication-reference", "integration.fx.reconciliation-reference",
                "integration.consent.approved-version", "integration.consent.jurisdictions",
                "integration.consent.retention-policy", "integration.consent.accessibility-reference",
                "integration.iam.issuer", "integration.iam.audience", "integration.iam.role-mappings",
                "integration.iam.access-review-reference", "integration.iam.revocation-feed")) {
            environment.setProperty(key, "evidence");
        }
        return environment;
    }
}
