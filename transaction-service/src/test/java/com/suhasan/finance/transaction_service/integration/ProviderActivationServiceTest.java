package com.suhasan.finance.transaction_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderActivationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final ProviderActivationService service =
            new ProviderActivationService(jdbc, new ObjectMapper(), environment);

    @Test
    void configurationRejectsCredentialMaterialAndRequiresRecentMfaEvidence() {
        assertThatThrownBy(() -> service.create(createRequest("password=do-not-store"),
                operator("configurer"), "provider-create-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("external reference");

        assertThatThrownBy(() -> service.create(createRequest("vault://notification/credential"),
                new ProviderActivationService.OperatorContext("configurer", null, Instant.now()),
                "provider-create-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MFA");
    }

    @Test
    void sandboxCertificationEnforcesSeparationOfDuties() {
        activation(Map.of(
                "activation_id", "activation-1",
                "boundary_type", "NOTIFICATION",
                "lifecycle_status", "DRAFT",
                "created_by", "configurer"));

        assertThatThrownBy(() -> service.certify("activation-1",
                certification("DELIVERY", "RECONCILIATION", "FAILURE_BEHAVIOR", "WEBHOOK_REPLAY"),
                operator("configurer"), "certify-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different operator");
    }

    @Test
    void productionActivationRemainsDisabledOutsideExplicitProductionProfile() {
        activation(Map.of(
                "activation_id", "activation-1",
                "boundary_type", "RISK",
                "lifecycle_status", "APPROVED",
                "approved_by", "approver"));

        assertThatThrownBy(() -> service.activate("activation-1",
                new ProviderActivationService.ActivationRequest("change://approved/1"),
                operator("deployer"), "activate-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void productionActivationRejectsLocalOrPlaceholderReferences() {
        environment.setActiveProfiles("production");
        environment.setProperty("integration.activation.production-enabled", "true");
        activation(Map.ofEntries(
                Map.entry("activation_id", "activation-1"),
                Map.entry("boundary_type", "RISK"),
                Map.entry("lifecycle_status", "APPROVED"),
                Map.entry("approved_by", "approver"),
                Map.entry("contract_reference", "local-development"),
                Map.entry("credential_reference", "vault://risk/credential"),
                Map.entry("security_review_reference", "review://security/1"),
                Map.entry("sla_evidence_reference", "evidence://sla/1"),
                Map.entry("rollback_reference", "exercise://rollback/1"),
                Map.entry("disaster_recovery_reference", "exercise://dr/1"),
                Map.entry("legal_approval_reference", "evidence://legal/1"),
                Map.entry("jurisdiction_review_reference", "evidence://jurisdiction/1")));

        assertThatThrownBy(() -> service.activate("activation-1",
                new ProviderActivationService.ActivationRequest("change://approved/1"),
                operator("deployer"), "activate-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-production");
    }

    @Test
    void certificationProfilesRequireExternalEvidenceReferences() {
        activation(Map.of(
                "activation_id", "activation-1",
                "boundary_type", "FX",
                "lifecycle_status", "DRAFT",
                "created_by", "configurer"));

        assertThatThrownBy(() -> service.certify("activation-1",
                new ProviderActivationService.CertificationRequest(
                        Set.of("FORECAST_ONLY", "PROVENANCE"),
                        null, "evidence://sla/1", "exercise://rollback/1", "exercise://dr/1"),
                operator("certifier"), "certify-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("certification evidence");
    }

    private void activation(Map<String, Object> activation) {
        when(jdbc.queryForList(contains("provider_activations"), eq("activation-1")))
                .thenReturn(List.of(activation));
    }

    private ProviderActivationService.CreateRequest createRequest(String credentialReference) {
        return new ProviderActivationService.CreateRequest(
                "NOTIFICATION", "provider-neutral-sandbox", "contract://pending/1",
                credentialReference, "v1", null, null, "evidence://sla/1",
                "feed://reconciliation/1", "verifier://webhook/1", "review://security/1",
                null, null, "exercise://rollback/1", "exercise://dr/1");
    }

    private ProviderActivationService.CertificationRequest certification(String... checks) {
        return new ProviderActivationService.CertificationRequest(
                Set.of(checks), "evidence://certification/1", "evidence://sla/1",
                "exercise://rollback/1", "exercise://dr/1");
    }

    private ProviderActivationService.OperatorContext operator(String actor) {
        return new ProviderActivationService.OperatorContext(actor, "iam://mfa/" + actor, Instant.now());
    }
}
