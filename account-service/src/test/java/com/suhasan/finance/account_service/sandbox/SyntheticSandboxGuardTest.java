package com.suhasan.finance.account_service.sandbox;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticSandboxGuardTest {
    @Test
    void enablesControlsOnlyInDedicatedSyntheticProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("synthetic-sandbox");
        SyntheticSandboxGuard guard = new SyntheticSandboxGuard(environment, true);
        guard.validateConfiguration();
        assertThat(guard.isSynthetic()).isTrue();
    }

    @Test
    void failsClosedWhenSyntheticControlsAreEnabledInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production", "synthetic-sandbox");
        SyntheticSandboxGuard guard = new SyntheticSandboxGuard(environment, true);
        assertThatThrownBy(guard::validateConfiguration).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forbidden in production");
    }

    @Test
    void deniesResetSeedControlsOutsideSyntheticProfile() {
        SyntheticSandboxGuard guard = new SyntheticSandboxGuard(new MockEnvironment(), true);
        assertThatThrownBy(guard::requireSynthetic).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }
}
