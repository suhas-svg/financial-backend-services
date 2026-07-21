package com.suhasan.finance.account_service.sandbox;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class SyntheticSandboxGuard {
    private final Environment environment;
    private final boolean enabled;

    public SyntheticSandboxGuard(Environment environment,
                                 @Value("${sandbox.synthetic.enabled:false}") boolean enabled) {
        this.environment = environment;
        this.enabled = enabled;
    }

    @PostConstruct
    void validateConfiguration() {
        if (enabled && !isSyntheticProfile()) {
            throw new IllegalStateException(
                    "Synthetic sandbox controls require the synthetic-sandbox profile and are forbidden in production");
        }
    }

    public void requireSynthetic() {
        if (!enabled || !isSyntheticProfile()) {
            throw new IllegalStateException("Synthetic sandbox controls are disabled in this runtime profile");
        }
    }

    public boolean isSynthetic() {
        return enabled && isSyntheticProfile();
    }

    private boolean isSyntheticProfile() {
        return environment.acceptsProfiles(Profiles.of("synthetic-sandbox"))
                && !environment.acceptsProfiles(Profiles.of("prod", "production"));
    }
}
