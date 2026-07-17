package com.suhasan.finance.account_service.integration;

import java.time.Instant;

public interface MfaSecretManager {
    Ciphertext encrypt(String plaintext);
    String decrypt(String ciphertext, String keyId);
    Health health();

    record Ciphertext(String value, String keyId) {}
    record Health(String provider, String activeKeyId, boolean configured, boolean healthy,
                  String classification, Instant checkedAt, String evidenceReference) {}
}
