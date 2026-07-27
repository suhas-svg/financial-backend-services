package com.suhasan.finance.transaction_service.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.suhasan.finance.transaction_service.service.SpendingLimitReservationLifecycleClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpendingLimitReservationCrossServiceIntegrationTest {
    private WireMockServer accountService;
    private SpendingLimitReservationLifecycleClient client;

    @BeforeEach
    void setUp() {
        accountService = new WireMockServer(0);
        accountService.start();
        client = new SpendingLimitReservationLifecycleClient(WebClient.builder());
        ReflectionTestUtils.setField(client, "accountServiceBaseUrl", accountService.baseUrl());
        ReflectionTestUtils.setField(client, "timeout", 5000);
        ReflectionTestUtils.setField(client, "internalJwtSecret",
                "cross-service-test-internal-signing-secret-at-least-32-bytes");
    }

    @AfterEach
    void tearDown() {
        accountService.stop();
    }

    @Test
    void transactionServiceReceivesOriginalReservationIdentityPayloadAndFingerprint() {
        accountService.stubFor(post(urlEqualTo("/api/internal/accounts/7/spending-limit-reservations"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "allowed": true,
                                  "replay": true,
                                  "currency": "USD",
                                  "dailyLimit": 100.00,
                                  "dailyUsed": 25.00,
                                  "remaining": 75.00,
                                  "reason": null,
                                  "reservationId": 44,
                                  "transactionCorrelation": "claim-1",
                                  "amount": 25.00,
                                  "fingerprint": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                                  "state": "RESERVED",
                                  "createdAt": "2026-07-27T10:00:00",
                                  "updatedAt": "2026-07-27T10:00:00",
                                  "expiresAt": "2026-07-27T10:30:00",
                                  "outcome": null
                                }
                                """)));

        SpendingLimitReservationLifecycleClient.ReservationResponse response = client.reserve(
                "7", "TRANSFER", new BigDecimal("25.00"), "key-1", "alice", "USD", "claim-1");

        assertThat(response.replay()).isTrue();
        assertThat(response.reservationId()).isEqualTo(44L);
        assertThat(response.amount()).isEqualByComparingTo("25.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.fingerprint()).hasSize(64);
        assertThat(response.transactionCorrelation()).isEqualTo("claim-1");

        accountService.verify(postRequestedFor(urlEqualTo(
                "/api/internal/accounts/7/spending-limit-reservations"))
                .withRequestBody(equalToJson("""
                        {
                          "operationType": "TRANSFER",
                          "amount": 25.00,
                          "idempotencyKey": "key-1",
                          "userId": "alice",
                          "currency": "USD",
                          "transactionCorrelation": "claim-1"
                        }
                        """, true, true)));
    }

    @Test
    void accountServicePayloadConflictSurfacesAsHttp409SemanticConflict() {
        accountService.stubFor(post(urlEqualTo("/api/internal/accounts/7/spending-limit-reservations"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":"Conflict","message":"Idempotency-Key was reused with a different spending-limit reservation payload"}
                                """)));

        assertThatThrownBy(() -> client.reserve(
                "7", "TRANSFER", new BigDecimal("30.00"), "key-1", "alice", "USD", "claim-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different spending-limit reservation payload");
    }
}
