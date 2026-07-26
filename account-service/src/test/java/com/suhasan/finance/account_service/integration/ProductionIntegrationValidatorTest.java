package com.suhasan.finance.account_service.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionIntegrationValidatorTest {
    private static final String[] REQUIRED = {
            "integration.notification.contract-id", "integration.iam.issuer",
            "integration.iam.audience", "integration.iam.role-mappings",
            "integration.iam.access-review-reference", "integration.iam.revocation-feed",
            "integration.mfa.kms.provider", "integration.mfa.kms.active-key-id",
            "integration.mfa.kms.health-reference"
    };

    @Test
    void ignoresNonProductionProfile() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        assertThatCode(() -> new ProductionIntegrationValidator(environment, mock(NotificationProvider.class)).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingBlankAndUnresolvedConfiguration() {
        Environment missing = configuredEnvironment();
        when(missing.getProperty(REQUIRED[0])).thenReturn(null);
        assertThatThrownBy(() -> validator(missing, healthy("external")).validate())
                .hasMessageContaining(REQUIRED[0]);

        Environment blank = configuredEnvironment();
        when(blank.getProperty(REQUIRED[0])).thenReturn(" ");
        assertThatThrownBy(() -> validator(blank, healthy("external")).validate())
                .hasMessageContaining(REQUIRED[0]);

        Environment unresolved = configuredEnvironment();
        when(unresolved.getProperty(REQUIRED[0])).thenReturn("${MISSING}");
        assertThatThrownBy(() -> validator(unresolved, healthy("external")).validate())
                .hasMessageContaining(REQUIRED[0]);
    }

    @Test
    void requiresHealthyExternalProviderAndAcceptsCompleteBoundary() {
        Environment environment = configuredEnvironment();
        assertThatThrownBy(() -> validator(environment, health("external", false, true)).validate())
                .hasMessageContaining("externally configured and healthy");
        assertThatThrownBy(() -> validator(environment, health("external", true, false)).validate())
                .hasMessageContaining("externally configured and healthy");
        assertThatThrownBy(() -> validator(environment, healthy("local-key")).validate())
                .hasMessageContaining("externally configured and healthy");
        assertThatCode(() -> validator(environment, healthy("contracted-provider")).validate())
                .doesNotThrowAnyException();
    }

    private Environment configuredEnvironment() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"production"});
        for (String key : REQUIRED) when(environment.getProperty(key)).thenReturn("configured");
        return environment;
    }

    private ProductionIntegrationValidator validator(Environment environment, NotificationProvider provider) {
        return new ProductionIntegrationValidator(environment, provider);
    }

    private NotificationProvider healthy(String provider) {
        return health(provider, true, true);
    }

    private NotificationProvider health(String name, boolean configured, boolean healthy) {
        NotificationProvider provider = mock(NotificationProvider.class);
        when(provider.health()).thenReturn(new NotificationProvider.ProviderHealth(
                name, configured, healthy, healthy ? "HEALTHY" : "UNAVAILABLE", Instant.now(), "evidence"));
        return provider;
    }
}
