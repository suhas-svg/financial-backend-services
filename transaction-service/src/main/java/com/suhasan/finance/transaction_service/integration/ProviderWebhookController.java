package com.suhasan.finance.transaction_service.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProviderWebhookController {
    private final ProviderWebhookReplayService webhooks;

    @PostMapping("/api/provider-activations/{activationId}/webhooks")
    public Map<String, Object> webhook(@PathVariable String activationId,
            @RequestHeader("X-Provider-Delivery-Id") String deliveryId,
            @RequestHeader("X-Provider-Timestamp") Instant providerTimestamp,
            @RequestHeader("X-Provider-Signature") String signature,
            @RequestBody byte[] payload) {
        return webhooks.verify(activationId, deliveryId, providerTimestamp, signature, payload);
    }
}
