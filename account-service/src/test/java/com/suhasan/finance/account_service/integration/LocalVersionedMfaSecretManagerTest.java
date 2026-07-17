package com.suhasan.finance.account_service.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalVersionedMfaSecretManagerTest {
    @Test
    void ciphertextCarriesVersionedIdentifierAndNeverReturnsPlaintextAsEvidence() {
        var manager = new LocalVersionedMfaSecretManager(
                "local-key", "key-2026-07", "unit-test-secret-material", "");

        var encrypted = manager.encrypt("TOTP-SECRET");

        assertThat(encrypted.keyId()).isEqualTo("key-2026-07");
        assertThat(encrypted.value()).startsWith("kms:v1:key-2026-07:");
        assertThat(encrypted.value()).doesNotContain("TOTP-SECRET");
        assertThat(manager.decrypt(encrypted.value(), encrypted.keyId())).isEqualTo("TOTP-SECRET");
        assertThat(manager.health().classification()).isEqualTo("NON_PRODUCTION_ADAPTER");
    }

    @Test
    void keyVersionMismatchFailsClosed() {
        var manager = new LocalVersionedMfaSecretManager(
                "local-key", "key-current", "unit-test-secret-material", "");
        var encrypted = manager.encrypt("secret");

        assertThatThrownBy(() -> manager.decrypt(encrypted.value(), "unknown-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decryption failed");
    }
}
