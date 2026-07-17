package com.suhasan.finance.transaction_service.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnMissingBean(ProviderWebhookVerifier.class)
public class FailClosedProviderWebhookVerifier implements ProviderWebhookVerifier {
    @Override
    public Verification verify(String activationId, String deliveryId, Instant providerTimestamp,
                               String signature, byte[] payload) {
        return new Verification(false, "unconfigured-provider-verifier", "FAIL_CLOSED");
    }
}
