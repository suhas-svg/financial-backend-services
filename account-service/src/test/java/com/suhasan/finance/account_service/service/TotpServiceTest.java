package com.suhasan.finance.account_service.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {
    private final TotpService service = new TotpService();

    @Test
    void matchesRfc6238Sha1VectorTruncatedToSixDigits() {
        String rfc6238Base32TestVector = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertThat(service.generateCode(rfc6238Base32TestVector, 1)).isEqualTo("287082");
        assertThat(service.verify(rfc6238Base32TestVector, "287082", Instant.ofEpochSecond(59))).isTrue();
        assertThat(service.verify(rfc6238Base32TestVector, "000000", Instant.ofEpochSecond(59))).isFalse();
    }

    @Test
    void generatedSecretsAndProvisioningUrisAreAuthenticatorCompatible() {
        String secret = service.generateSecret();
        assertThat(secret).matches("[A-Z2-7]{32}");
        assertThat(service.provisioningUri("alice@example.com", secret))
                .startsWith("otpauth://totp/Financial%20Backend%3Aalice%40example.com")
                .contains("secret=" + secret)
                .contains("issuer=Financial%20Backend");
    }
}
