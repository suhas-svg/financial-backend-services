package com.suhasan.finance.transaction_service.integration;

import java.time.Instant;

/**
 * Provider-specific signature verification boundary. A real implementation may
 * be supplied only after a provider and its webhook contract are approved.
 */
public interface ProviderWebhookVerifier {
    Verification verify(String activationId, String deliveryId, Instant providerTimestamp,
                        String signature, byte[] payload);

    record Verification(boolean verified, String verifierReference, String classification) {}
}
