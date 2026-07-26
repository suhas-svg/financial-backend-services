package com.suhasan.finance.account_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Service
@SuppressWarnings("PMD.HardCodedCryptoKey") // "AES" selects an algorithm; key material comes from configuration.
public class SecretEncryptionService {
    private static final int IV_BYTES = 12;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    public SecretEncryptionService(@Value("${security.mfa.encryption-key:}") final String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            this.key = null;
            return;
        }
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(configuredKey.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Unable to initialize MFA encryption", e);
        }
    }

    public String encrypt(final String plaintext) {
        requireConfigured();
        try {
            final byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Unable to encrypt MFA secret", e);
        }
    }

    public String decrypt(final String encoded) {
        requireConfigured();
        try {
            final ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
            final byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            final byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Unable to decrypt MFA secret", e);
        }
    }

    private void requireConfigured() {
        if (key == null) {
            throw new IllegalStateException("MFA encryption key is not configured");
        }
    }
}
