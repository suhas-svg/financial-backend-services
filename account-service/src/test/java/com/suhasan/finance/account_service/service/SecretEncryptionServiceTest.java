package com.suhasan.finance.account_service.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretEncryptionServiceTest {
    @Test
    void encryptsAndDecryptsWithConfiguredKey() {
        SecretEncryptionService service = new SecretEncryptionService(
                "test-only-key-with-at-least-thirty-two-characters");
        String encrypted = service.encrypt("totp-secret");

        assertThat(encrypted).doesNotContain("totp-secret");
        assertThat(service.decrypt(encrypted)).isEqualTo("totp-secret");
    }

    @Test
    void failsClosedWhenEncryptionKeyIsMissing() {
        SecretEncryptionService service = new SecretEncryptionService("");
        assertThatThrownBy(() -> service.encrypt("totp-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MFA encryption key is not configured");
    }
}
