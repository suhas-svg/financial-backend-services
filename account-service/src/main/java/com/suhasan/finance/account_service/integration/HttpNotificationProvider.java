package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.entity.Notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "integration.notification.provider", havingValue = "http")
@SuppressWarnings({
        "PMD.AvoidDuplicateLiterals", // Provider identifier is intentionally repeated in evidence records.
        "PMD.AvoidLiteralsInIfCondition" // HTTP status values are protocol constants.
})
public class HttpNotificationProvider implements NotificationProvider {
    private final RestClient client;
    private final String contractId;
    private final String evidenceReference;

    public HttpNotificationProvider(
            @Value("${integration.notification.endpoint}") final String endpoint,
            @Value("${integration.notification.bearer-token}") final String bearerToken,
            @Value("${integration.notification.contract-id}") final String contractId,
            @Value("${integration.notification.health-evidence-reference:runtime-health}") final String evidenceReference) {
        if (endpoint == null || endpoint.isBlank() || bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalStateException("HTTP notification endpoint and secret-manager supplied token are required");
        }
        this.client = RestClient.builder().baseUrl(endpoint.trim())
                .defaultHeader("Authorization", "Bearer " + bearerToken).build();
        this.contractId = contractId;
        this.evidenceReference = evidenceReference;
    }

    @Override
    public ProviderReceipt deliver(final Notification notification) {
        final Instant attempted = Instant.now();
        try {
            final ProviderResponse response = client.post().uri("/v1/messages")
                    .body(Map.of(
                            "deliveryId", notification.getDeliveryId() == null
                                    ? "notification-" + notification.getNotificationId() : notification.getDeliveryId(),
                            "recipientReference", notification.getUserId(),
                            "title", notification.getTitle(),
                            "message", notification.getMessage(),
                            "contractId", contractId))
                    .retrieve().body(ProviderResponse.class);
            if (response == null || response.receiptId() == null || response.receiptId().isBlank()) {
                return new ProviderReceipt("http", null, Classification.REJECTED,
                        "UNRECONCILED", attempted, "Provider response omitted a receipt identifier");
            }
            return new ProviderReceipt("http", response.receiptId(), Classification.ACCEPTED,
                    response.reconciliationStatus() == null ? "PENDING" : response.reconciliationStatus(),
                    attempted, "Provider accepted the message; customer receipt is not claimed");
        } catch (ResourceAccessException timeout) {
            return new ProviderReceipt("http", null, Classification.TIMEOUT,
                    "UNRECONCILED", attempted, "Provider timed out");
        } catch (RestClientResponseException response) {
            final Classification classification = classify(response.getStatusCode());
            return new ProviderReceipt("http", null, classification,
                    "UNRECONCILED", attempted, "Provider returned HTTP " + response.getStatusCode().value());
        }
    }

    @Override
    public ProviderHealth health() {
        try {
            client.get().uri("/health").retrieve().toBodilessEntity();
            return new ProviderHealth("http", true, true, "HEALTHY",
                    Instant.now(), evidenceReference);
        } catch (RuntimeException failure) {
            return new ProviderHealth("http", true, false, "UNAVAILABLE",
                    Instant.now(), evidenceReference);
        }
    }

    private Classification classify(final HttpStatusCode code) {
        if (code.value() == 429) return Classification.RATE_LIMITED;
        if (code.is5xxServerError()) return Classification.UNAVAILABLE;
        if (code.value() == 404 || code.value() == 422) return Classification.INVALID_DESTINATION;
        return Classification.REJECTED;
    }

    private record ProviderResponse(String receiptId, String reconciliationStatus) {}
}
