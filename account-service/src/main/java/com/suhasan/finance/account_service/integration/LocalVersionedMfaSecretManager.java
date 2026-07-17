package com.suhasan.finance.account_service.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LocalVersionedMfaSecretManager implements MfaSecretManager {
    private final SecureRandom random = new SecureRandom();
    private final String provider;
    private final String activeKeyId;
    private final Map<String, SecretKeySpec> keys;

    public LocalVersionedMfaSecretManager(
            @Value("${integration.mfa.kms.provider:local-key}") String provider,
            @Value("${integration.mfa.kms.active-key-id:local-v1}") String activeKeyId,
            @Value("${security.mfa.encryption-key:}") String activeSecret,
            @Value("${integration.mfa.kms.previous-keys:}") String previousKeys) {
        this.provider = provider == null ? "unconfigured" : provider.trim();
        this.activeKeyId = requireKeyId(activeKeyId);
        Map<String, SecretKeySpec> configured = new LinkedHashMap<>();
        if (activeSecret != null && !activeSecret.isBlank()) {
            configured.put(this.activeKeyId, derive(activeSecret));
            configured.put("legacy", derive(activeSecret));
        }
        if (previousKeys != null && !previousKeys.isBlank()) {
            for (String entry : previousKeys.split(";")) {
                String[] pair = entry.trim().split("=", 2);
                if (pair.length != 2 || pair[1].isBlank()) {
                    throw new IllegalStateException("MFA previous keys must use key-id=secret entries");
                }
                configured.put(requireKeyId(pair[0]), derive(pair[1]));
            }
        }
        this.keys = Map.copyOf(configured);
    }

    @Override
    public Ciphertext encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, require(activeKeyId), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new Ciphertext("kms:v1:" + activeKeyId + ":" + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array()), activeKeyId);
        } catch (Exception ex) {
            throw new IllegalStateException("MFA secret encryption failed", ex);
        }
    }

    @Override
    public String decrypt(String ciphertext, String keyId) {
        try {
            String payload = ciphertext;
            String resolved = keyId == null || keyId.isBlank() ? "legacy" : keyId;
            if (ciphertext.startsWith("kms:v1:")) {
                String[] parts = ciphertext.split(":", 4);
                if (parts.length != 4 || !parts[2].equals(resolved)) {
                    throw new IllegalStateException("MFA ciphertext key version mismatch");
                }
                payload = parts[3];
            }
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(payload));
            byte[] iv = new byte[12];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, require(resolved), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("MFA secret decryption failed", ex);
        }
    }

    @Override
    public Health health() {
        boolean configured = keys.containsKey(activeKeyId);
        boolean local = provider.startsWith("local");
        return new Health(provider, activeKeyId, configured, configured,
                local ? "NON_PRODUCTION_ADAPTER" : configured ? "BOUNDARY_CONFIGURED" : "FAIL_CLOSED",
                Instant.now(), "key-id:" + activeKeyId);
    }

    private SecretKeySpec derive(String secret) {
        try {
            return new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Could not initialize MFA key", ex);
        }
    }
    private SecretKeySpec require(String keyId) {
        SecretKeySpec key = keys.get(keyId);
        if (key == null) throw new IllegalStateException("MFA KMS key version is unavailable: " + keyId);
        return key;
    }
    private String requireKeyId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalStateException("Invalid MFA key identifier");
        return normalized;
    }
}
