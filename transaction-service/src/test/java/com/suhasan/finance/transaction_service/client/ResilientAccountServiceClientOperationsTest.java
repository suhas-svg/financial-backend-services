package com.suhasan.finance.transaction_service.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.suhasan.finance.transaction_service.dto.StepUpClientDtos;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientAccountServiceClientOperationsTest {
    private WireMockServer server;
    private Retry retry;
    private ResilientAccountServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        retry = Retry.of("operations-test", RetryConfig.custom()
                .maxAttempts(1).waitDuration(Duration.ZERO).build());
        client = new ResilientAccountServiceClient(
                WebClient.builder(),
                retry,
                CircuitBreaker.ofDefaults("operations-test"),
                TimeLimiter.of("operations-test", TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(10)).build()));
        ReflectionTestUtils.setField(client, "accountServiceBaseUrl", server.baseUrl());
        ReflectionTestUtils.setField(client, "timeout", 10_000);
        ReflectionTestUtils.setField(client, "internalJwtSecret",
                "internal-jwt-secret-that-is-at-least-32-bytes");
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void idempotentBalanceOperationUsesInternalLedgerEndpoint() {
        server.stubFor(post(urlEqualTo("/api/internal/accounts/42/balance-ops"))
                .willReturn(okJson("""
                        {"accountId":42,"operationId":"op-1","applied":true,
                         "newBalance":125.00,"version":3,"status":"ACTIVE"}
                        """)));
        var response = client.applyBalanceOperation(
                "42", "op-1", new BigDecimal("25.00"), "transaction-1", "DEPOSIT", false);

        assertThat(response.getAccountId()).isEqualTo(42L);
        assertThat(response.getOperationId()).isEqualTo("op-1");
        assertThat(response.isApplied()).isTrue();
        assertThat(response.getNewBalance()).isEqualByComparingTo("125.00");
        server.verify(postRequestedFor(urlEqualTo("/api/internal/accounts/42/balance-ops"))
                .withRequestBody(matchingJsonPath("$.operationId", equalTo("op-1")))
                .withRequestBody(matchingJsonPath("$.allowNegative", equalTo("false"))));
    }

    @Test
    void debitHoldLifecycleUsesPlaceCaptureAndReleaseActions() {
        server.stubFor(post(urlEqualTo("/api/internal/accounts/42/holds"))
                .willReturn(okJson(holdJson("PLACED"))));
        server.stubFor(post(urlEqualTo("/api/internal/accounts/42/holds/hold-1/capture"))
                .willReturn(okJson(holdJson("CAPTURED"))));
        server.stubFor(post(urlEqualTo("/api/internal/accounts/42/holds/hold-1/release"))
                .willReturn(okJson(holdJson("RELEASED"))));

        var placed = client.placeDebitHold(
                "42", "hold-1", new BigDecimal("20.00"), "transaction-1", "WITHDRAWAL");
        var captured = client.captureDebitHold("42", "hold-1", "transaction-1", "CAPTURE");
        var released = client.releaseDebitHold("42", "hold-1", "transaction-1", "RELEASE");

        assertThat(placed.getStatus()).isEqualTo("PLACED");
        assertThat(captured.getStatus()).isEqualTo("CAPTURED");
        assertThat(released.getStatus()).isEqualTo("RELEASED");
        server.verify(postRequestedFor(urlEqualTo("/api/internal/accounts/42/holds"))
                .withRequestBody(matchingJsonPath("$.holdId", equalTo("hold-1")))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("20.0"))));
        server.verify(postRequestedFor(urlEqualTo("/api/internal/accounts/42/holds/hold-1/capture")));
        server.verify(postRequestedFor(urlEqualTo("/api/internal/accounts/42/holds/hold-1/release")));
    }

    @Test
    void internalAccountAndBeneficiaryLookupsDeserializeResponses() {
        server.stubFor(get(urlEqualTo("/api/internal/accounts/42"))
                .willReturn(okJson("""
                        {"id":42,"ownerId":"alice","balance":90.00,"accountType":"CHECKING","active":true}
                        """)));
        server.stubFor(get(urlPathEqualTo("/api/internal/beneficiaries/beneficiary-1"))
                .withQueryParam("userId", equalTo("alice"))
                .willReturn(okJson("""
                        {"beneficiaryId":"beneficiary-1","userId":"alice",
                         "destinationAccountId":"99","currency":"USD","status":"ACTIVE",
                         "createdAt":"2026-07-13T12:00:00"}
                        """)));

        var account = client.getAccountInternal("42");
        var beneficiary = client.getBeneficiary("beneficiary-1", "alice");

        assertThat(account.getOwnerId()).isEqualTo("alice");
        assertThat(beneficiary.getBeneficiaryId()).isEqualTo("beneficiary-1");
        assertThat(beneficiary.getDestinationAccountId()).isEqualTo("99");
    }

    @Test
    void stepUpChallengeCreationAndConsumptionHandleSuccessAndRejection() {
        server.stubFor(post(urlEqualTo("/api/internal/security/challenges"))
                .willReturn(okJson("""
                        {"challengeId":"challenge-1","expiresAt":"2026-07-13T12:05:00Z"}
                        """)));
        server.stubFor(post(urlEqualTo("/api/internal/security/challenges/challenge-1/consume"))
                .willReturn(okJson("""
                        {"consumed":true,"consumedAt":"2026-07-13T12:01:00Z"}
                        """)));
        var createRequest = new StepUpClientDtos.CreateChallengeRequest("alice", "TRANSFER", "fingerprint");
        var consumeRequest = new StepUpClientDtos.ConsumeChallengeRequest(
                "alice", "fingerprint", "authorization-1", "proof-1");

        assertThat(client.createStepUpChallenge(createRequest).challengeId()).isEqualTo("challenge-1");
        assertThat(client.consumeStepUpChallenge("challenge-1", consumeRequest).consumed()).isTrue();

        server.resetAll();
        server.stubFor(post(urlEqualTo("/api/internal/security/challenges/challenge-1/consume"))
                .willReturn(aResponse().withStatus(400)));
        assertThatThrownBy(() -> client.consumeStepUpChallenge("challenge-1", consumeRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Step-up proof was rejected");
    }

    @Test
    void spendingLimitReservationAndReleaseUseStableIdempotencyCoordinates() {
        server.stubFor(post(urlEqualTo("/api/internal/accounts/42/spending-limit-reservations"))
                .willReturn(okJson("""
                        {"allowed":true,"replay":false,"dailyLimit":1000.00,
                         "dailyUsed":200.00,"remaining":800.00,"reason":null}
                        """)));
        server.stubFor(delete(urlPathEqualTo(
                "/api/internal/accounts/42/spending-limit-reservations/TRANSFER/request-1"))
                .withQueryParam("userId", equalTo("alice"))
                .willReturn(aResponse().withStatus(204)));

        var reservation = client.reserveSpendingLimit(
                "42", "TRANSFER", new BigDecimal("25.00"), "request-1", "alice");
        client.releaseSpendingLimit("42", "TRANSFER", "request-1", "alice");

        assertThat(reservation.isAllowed()).isTrue();
        assertThat(reservation.isReplay()).isFalse();
        assertThat(reservation.getRemaining()).isEqualByComparingTo("800.00");
        server.verify(postRequestedFor(urlEqualTo("/api/internal/accounts/42/spending-limit-reservations"))
                .withRequestBody(matchingJsonPath("$.idempotencyKey", equalTo("request-1")))
                .withRequestBody(matchingJsonPath("$.userId", equalTo("alice"))));
    }

    @Test
    void ledgerProjectionDistinguishesSuccessFromTerminalConflictsAndRejections() {
        var request = new ResilientAccountServiceClient.LedgerProjectionUpdateRequest(
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("90.00"),
                "USD", 5L, "event-1", LocalDateTime.parse("2026-07-13T12:00:00"));
        server.stubFor(put(urlEqualTo("/api/internal/accounts/42/ledger-projection"))
                .willReturn(okJson("""
                        {"id":42,"ledgerBalance":100.00,"pendingBalance":10.00,
                         "availableBalance":90.00,"currency":"USD","ledgerProjectionVersion":5,
                         "ledgerProjectionSourceEventId":"event-1"}
                        """)));

        var response = client.applyLedgerProjection("42", request);
        assertThat(response.getLedgerProjectionVersion()).isEqualTo(5L);
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("90.00");

        server.resetAll();
        server.stubFor(put(urlEqualTo("/api/internal/accounts/42/ledger-projection"))
                .willReturn(aResponse().withStatus(409).withBody("stale version")));
        assertThatThrownBy(() -> client.applyLedgerProjection("42", request))
                .isInstanceOfSatisfying(
                        ResilientAccountServiceClient.LedgerProjectionDeliveryException.class,
                        error -> {
                            assertThat(error.terminal()).isTrue();
                            assertThat(error).hasMessageContaining("projection conflict");
                        });

        server.resetAll();
        server.stubFor(put(urlEqualTo("/api/internal/accounts/42/ledger-projection"))
                .willReturn(aResponse().withStatus(400).withBody("invalid projection")));
        assertThatThrownBy(() -> client.applyLedgerProjection("42", request))
                .isInstanceOfSatisfying(
                        ResilientAccountServiceClient.LedgerProjectionDeliveryException.class,
                        error -> {
                            assertThat(error.terminal()).isTrue();
                            assertThat(error).hasMessageContaining("projection rejected");
                        });
        assertThat(client.getRetryMetrics()).isSameAs(retry.getMetrics());
    }

    private String holdJson(String status) {
        return """
                {"holdId":"hold-1","accountId":42,"applied":true,
                 "ledgerBalance":100.00,"availableBalance":80.00,"version":2,
                 "status":"%s","message":null}
                """.formatted(status);
    }
}
