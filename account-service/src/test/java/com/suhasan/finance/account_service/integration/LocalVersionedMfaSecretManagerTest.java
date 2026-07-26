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

    @Test
    void supportsLegacyCiphertextAndPreviousKeyRotation() {
        var oldManager = new LocalVersionedMfaSecretManager(
                "local-key", "key-old", "old-secret-material", "");
        var oldCiphertext = oldManager.encrypt("rotated-secret");
        String legacyPayload = oldCiphertext.value().substring(oldCiphertext.value().lastIndexOf(':') + 1);

        var rotatedManager = new LocalVersionedMfaSecretManager(
                "external-kms", "key-current", "current-secret-material",
                "key-old=old-secret-material; key-spare=spare-secret-material");

        assertThat(rotatedManager.decrypt(oldCiphertext.value(), "key-old")).isEqualTo("rotated-secret");
        assertThat(oldManager.decrypt(legacyPayload, null)).isEqualTo("rotated-secret");
        assertThat(oldManager.decrypt(legacyPayload, " ")).isEqualTo("rotated-secret");
        assertThat(rotatedManager.health().classification()).isEqualTo("BOUNDARY_CONFIGURED");
    }

    @Test
    void missingActiveSecretIsFailClosed() {
        var manager = new LocalVersionedMfaSecretManager(null, "key-current", " ", null);

        assertThat(manager.health().provider()).isEqualTo("unconfigured");
        assertThat(manager.health().configured()).isFalse();
        assertThat(manager.health().classification()).isEqualTo("FAIL_CLOSED");
        assertThatThrownBy(() -> manager.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption failed");
    }

    @Test
    void malformedPreviousKeysAreRejected() {
        assertThatThrownBy(() -> new LocalVersionedMfaSecretManager(
                "local-key", "key-current", "current-secret", "missing-separator"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-id=secret");
        assertThatThrownBy(() -> new LocalVersionedMfaSecretManager(
                "local-key", "key-current", "current-secret", "key-old= "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-id=secret");
    }

    @Test
    void invalidKeyIdentifiersAndMalformedCiphertextsAreRejected() {
        assertThatThrownBy(() -> new LocalVersionedMfaSecretManager(
                "local-key", null, "secret", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid MFA key identifier");
        assertThatThrownBy(() -> new LocalVersionedMfaSecretManager(
                "local-key", "bad key", "secret", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid MFA key identifier");

        var manager = new LocalVersionedMfaSecretManager(
                "local-key", "key-current", "current-secret", "");
        assertThatThrownBy(() -> manager.decrypt("kms:v1:key-current", "key-current"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decryption failed");
        assertThatThrownBy(() -> manager.decrypt("not-base64", "key-current"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decryption failed");
    }
}
