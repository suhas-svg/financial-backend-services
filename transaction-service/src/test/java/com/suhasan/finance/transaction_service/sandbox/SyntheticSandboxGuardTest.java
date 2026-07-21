package com.suhasan.finance.transaction_service.sandbox;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticSandboxGuardTest {
    @Test
    void productionCanNeverEnableSyntheticSeedControls() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("synthetic-sandbox", "production");
        SyntheticSandboxGuard guard = new SyntheticSandboxGuard(environment, true);
        assertThatThrownBy(guard::validateConfiguration).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void realDataProfileDeniesSeedAndResetOperations() {
        SyntheticSandboxGuard guard = new SyntheticSandboxGuard(new MockEnvironment(), true);
        assertThatThrownBy(guard::requireSynthetic).isInstanceOf(IllegalStateException.class);
    }
}
