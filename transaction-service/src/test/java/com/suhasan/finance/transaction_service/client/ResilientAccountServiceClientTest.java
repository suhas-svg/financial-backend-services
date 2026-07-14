package com.suhasan.finance.transaction_service.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.StepUpClientDtos;
import com.suhasan.finance.transaction_service.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class ResilientAccountServiceClientTest {
    private static final String INTERNAL_SECRET = "test-only-" + "1".repeat(32);

    private WireMockServer server;
    private CircuitBreaker circuitBreaker;
    private ResilientAccountServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        Retry retry = Retry.of("account-test", RetryConfig.custom()
                .maxAttempts(1)
                .waitDuration(Duration.ZERO)
                .build());
        circuitBreaker = CircuitBreaker.ofDefaults("account-test");
        TimeLimiter timeLimiter = TimeLimiter.of("account-test", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .cancelRunningFuture(true)
                .build());
        client = new ResilientAccountServiceClient(WebClient.builder(), retry, circuitBreaker, timeLimiter);
        ReflectionTestUtils.setField(client, "accountServiceBaseUrl", server.baseUrl());
        ReflectionTestUtils.setField(client, "timeout", 10_000);
        ReflectionTestUtils.setField(client, "internalJwtSecret", INTERNAL_SECRET);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        server.stop();
    }

    @Test
    void getAccountReturnsDtoAndForwardsCurrentUserToken() {
        server.stubFor(get(urlEqualTo("/api/accounts/42"))
                .willReturn(okJson("""
                        {"id":42,"ownerId":"alice","balance":125.50,
                         "availableBalance":100.00,"currency":"USD","accountType":"CHECKING","active":true}
                        """)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "user-jwt-token"));

        AccountDto account = client.getAccount("42");

        assertThat(account.getId()).isEqualTo(42L);
        assertThat(account.getOwnerId()).isEqualTo("alice");
        assertThat(account.spendableBalance()).isEqualByComparingTo("100.00");
        server.verify(getRequestedFor(urlEqualTo("/api/accounts/42"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer user-jwt-token")));
    }

    @Test
    void getAccountMapsClientErrorsToMissingAndServerErrorsToUnavailable() {
        server.stubFor(get(urlEqualTo("/api/accounts/missing"))
                .willReturn(aResponse().withStatus(404)));
        server.stubFor(get(urlEqualTo("/api/accounts/failing"))
                .willReturn(aResponse().withStatus(503).withBody("maintenance")));

        assertThat(client.getAccount("missing")).isNull();
        assertThatThrownBy(() -> client.getAccount("failing"))
                .isInstanceOf(AccountServiceUnavailableException.class)
                .hasMessageContaining("Account service unavailable");
    }

    @Test
    void openCircuitProducesExplicitUnavailableErrorWithoutCallingAccountService() {
        circuitBreaker.transitionToOpenState();

        assertThatThrownBy(() -> client.getAccount("42"))
                .isInstanceOf(AccountServiceUnavailableException.class)
                .hasMessageContaining("circuit breaker open");

        server.verify(0, getRequestedFor(urlEqualTo("/api/accounts/42")));
        assertThat(client.getCircuitBreakerState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(client.getCircuitBreakerMetrics()).isSameAs(circuitBreaker.getMetrics());
        assertThat(client.getBaseUrl()).isEqualTo(server.baseUrl());
    }

    @Test
    void accountHelpersRespectActivityAvailableBalanceAndCreditLimit() {
        ResilientAccountServiceClient helperClient = spy(client);
        AccountDto checking = AccountDto.builder()
                .active(true).accountType("CHECKING")
                .balance(new BigDecimal("150.00"))
                .availableBalance(new BigDecimal("80.00"))
                .build();
        AccountDto credit = AccountDto.builder()
                .active(true).accountType("CREDIT")
                .availableCredit(new BigDecimal("500.00"))
                .build();
        AccountDto inactive = AccountDto.builder().active(false).accountType("CHECKING").build();
        doReturn(checking).when(helperClient).getAccount("checking");
        doReturn(credit).when(helperClient).getAccount("credit");
        doReturn(inactive).when(helperClient).getAccount("inactive");
        doReturn(null).when(helperClient).getAccount("missing");

        assertThat(helperClient.validateAccount("checking")).isTrue();
        assertThat(helperClient.validateAccount("inactive")).isFalse();
        assertThat(helperClient.validateAccount("missing")).isFalse();
        assertThat(helperClient.getAccountBalance("checking")).isEqualByComparingTo("150.00");
        assertThat(helperClient.getAccountBalance("missing")).isZero();
        assertThat(helperClient.hasSufficientBalance("checking", new BigDecimal("81.00"))).isFalse();
        assertThat(helperClient.hasSufficientBalance("checking", new BigDecimal("80.00"))).isTrue();
        assertThat(helperClient.hasSufficientBalance("credit", new BigDecimal("500.00"))).isTrue();
        assertThat(helperClient.hasSufficientBalance("credit", new BigDecimal("501.00"))).isFalse();
        assertThat(helperClient.hasSufficientBalance("missing", BigDecimal.ONE)).isFalse();
    }

    @Test
    void healthCheckReturnsTrueOnlyForUpResponse() {
        server.stubFor(get(urlEqualTo("/actuator/health"))
                .willReturn(okJson("{\"status\":\"UP\"}")));
        assertThat(client.checkHealth()).isTrue();

        server.resetAll();
        server.stubFor(get(urlEqualTo("/actuator/health"))
                .willReturn(aResponse().withStatus(503)));
        assertThat(client.checkHealth()).isFalse();
    }

    @Test
    void notificationUsesShortLivedInternalServiceTokenAndMapsFailures() {
        server.stubFor(post(urlEqualTo("/api/internal/notifications"))
                .willReturn(aResponse().withStatus(204)));
        var request = ResilientAccountServiceClient.NotificationRequest.builder()
                .userId("alice").type("SECURITY").severity("WARNING")
                .title("Review required").message("Verify transfer")
                .sourceType("TRANSACTION").sourceId("authorization-1").dedupeKey("dedupe-1")
                .build();

        client.createNotification(request);

        List<LoggedRequest> requests = server.findAll(postRequestedFor(urlEqualTo("/api/internal/notifications")));
        assertThat(requests).hasSize(1);
        String token = requests.get(0).getHeader(HttpHeaders.AUTHORIZATION).substring("Bearer ".length());
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(INTERNAL_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo("transaction-service");
        assertThat(claims.getAudience()).containsExactly("account-service");
        assertThat(claims.get("token_type")).isEqualTo("service");
        assertThat(claims.get("roles", List.class)).contains("ROLE_INTERNAL_SERVICE");

        server.resetAll();
        server.stubFor(post(urlEqualTo("/api/internal/notifications"))
                .willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> client.createNotification(request))
                .isInstanceOf(AccountServiceUnavailableException.class)
                .hasMessageContaining("notification creation");
    }

    @Test
    void challengeConflictIsReportedAsMfaEnrollmentRequirement() {
        server.stubFor(post(urlEqualTo("/api/internal/security/challenges"))
                .willReturn(aResponse().withStatus(409)));

        assertThatThrownBy(() -> client.createStepUpChallenge(
                new StepUpClientDtos.CreateChallengeRequest("alice", "TRANSFER", "fingerprint")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MFA_ENROLLMENT_REQUIRED");
    }
}
