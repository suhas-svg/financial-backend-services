package com.suhasan.finance.transaction_service.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderWebhookReplayService {
    private final JdbcTemplate jdbc;
    private final ProviderWebhookVerifier verifier;

    @Transactional
    public Map<String, Object> verify(String activationId, String deliveryId, Instant providerTimestamp,
                                      String signature, byte[] payload) {
        if (deliveryId == null || deliveryId.isBlank() || providerTimestamp == null
                || signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("Signed webhook delivery metadata is required");
        }
        Instant receivedAt = Instant.now();
        if (providerTimestamp.isAfter(receivedAt.plusSeconds(30))
                || Duration.between(providerTimestamp, receivedAt).abs().toMinutes() > 5) {
            throw new IllegalStateException("Webhook timestamp is outside the replay window");
        }
        var verification = verifier.verify(activationId, deliveryId.trim(), providerTimestamp,
                signature.trim(), payload == null ? new byte[0] : payload);
        if (!verification.verified()) {
            throw new IllegalStateException("Webhook signature verification failed closed");
        }
        String digest = digest(payload == null ? new byte[0] : payload);
        try {
            jdbc.update("""
                    INSERT INTO provider_webhook_replay_evidence
                    (replay_id,activation_id,delivery_id,payload_digest,verifier_reference,
                     provider_timestamp,received_at) VALUES (?,?,?,?,?,?,?)
                    """, UUID.randomUUID().toString(), activationId, deliveryId.trim(), digest,
                    verification.verifierReference(), Timestamp.from(providerTimestamp),
                    Timestamp.from(receivedAt));
        } catch (DuplicateKeyException replay) {
            throw new IllegalStateException("Webhook replay was rejected");
        }
        return Map.of(
                "accepted", true,
                "classification", verification.classification(),
                "deliveryId", deliveryId.trim(),
                "payloadDigest", digest,
                "receivedAt", receivedAt);
    }

    private String digest(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not fingerprint webhook payload", failure);
        }
    }
}
