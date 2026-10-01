package com.suhasan.finance.transaction_service.client;

import com.suhasan.finance.transaction_service.security.keys.TestKeys;
import com.suhasan.finance.transaction_service.security.InternalServiceTokens;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The account lookup returns mutable state (status, balances) that callers use to decide whether
 * a debit is allowed. Once it was cached for an hour, an admin freeze was invisible and a frozen
 * account could still be debited. These tests run the client behind a real Spring caching proxy,
 * so re-adding a cache annotation to it fails here.
 */
class AccountLookupNotCachedTest {

    /** Control: a plainly cacheable bean, to prove this harness really caches when asked to. */
    static class CountingLookup {
        final AtomicInteger calls = new AtomicInteger();

        @Cacheable("control")
        public int lookup(String id) {
            return calls.incrementAndGet();
        }
    }

    @Configuration
    @EnableCaching
    static class CachingProxyConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        CountingLookup countingLookup() {
            return new CountingLookup();
        }

        @Bean
        InternalServiceTokens internalServiceTokens() {
            return new InternalServiceTokens(TestKeys.internalSigningKeys());
        }

        @Bean
        ResilientAccountServiceClient client() {
            Retry retry = Retry.of("account-cache-test", RetryConfig.custom()
                    .maxAttempts(1).waitDuration(Duration.ZERO).build());
            TimeLimiter timeLimiter = TimeLimiter.of("account-cache-test", TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofSeconds(10)).cancelRunningFuture(true).build());
            return new ResilientAccountServiceClient(WebClient.builder(), retry,
                    CircuitBreaker.ofDefaults("account-cache-test"), timeLimiter);
        }
    }

    private WireMockServer server;
    private AnnotationConfigApplicationContext context;
    private ResilientAccountServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        context = new AnnotationConfigApplicationContext(CachingProxyConfig.class);
        client = context.getBean(ResilientAccountServiceClient.class);
        ReflectionTestUtils.setField(client, "accountServiceBaseUrl", server.baseUrl());
        ReflectionTestUtils.setField(client, "timeout", 10_000);
        ReflectionTestUtils.setField(client, "internalServiceTokens",
                new InternalServiceTokens(TestKeys.internalSigningKeys()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "user-jwt-token"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
        server.stop();
    }

    @Test
    void theHarnessCachesWhenAskedTo() {
        // Without this, the assertions below could pass simply because nothing is being cached here.
        CountingLookup control = context.getBean(CountingLookup.class);

        assertThat(control.lookup("x")).isEqualTo(1);
        assertThat(control.lookup("x")).as("a @Cacheable bean is served from the cache").isEqualTo(1);
    }

    @Test
    void anAdminFreezeIsSeenByTheVeryNextLookup() {
        server.stubFor(get(urlEqualTo("/api/accounts/119")).inScenario("freeze")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":119,\"ownerId\":\"alice\",\"status\":\"ACTIVE\",\"currency\":\"USD\"}"))
                .willSetStateTo("frozen"));
        server.stubFor(get(urlEqualTo("/api/accounts/119")).inScenario("freeze")
                .whenScenarioStateIs("frozen")
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":119,\"ownerId\":\"alice\",\"status\":\"FROZEN\",\"currency\":\"USD\"}")));

        AccountDto beforeFreeze = client.getAccount("119");
        AccountDto afterFreeze = client.getAccount("119");

        assertThat(beforeFreeze.allowsDebits()).isTrue();
        assertThat(afterFreeze.allowsDebits())
                .as("a frozen account must not look debit-eligible because of an earlier lookup")
                .isFalse();
        server.verify(2, getRequestedFor(urlEqualTo("/api/accounts/119")));
    }

    @Test
    void anUnfreezeIsAlsoSeenImmediately() {
        server.stubFor(get(urlEqualTo("/api/accounts/119")).inScenario("unfreeze")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":119,\"ownerId\":\"alice\",\"status\":\"FROZEN\",\"currency\":\"USD\"}"))
                .willSetStateTo("active"));
        server.stubFor(get(urlEqualTo("/api/accounts/119")).inScenario("unfreeze")
                .whenScenarioStateIs("active")
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":119,\"ownerId\":\"alice\",\"status\":\"ACTIVE\",\"currency\":\"USD\"}")));

        assertThat(client.getAccount("119").allowsDebits()).isFalse();
        assertThat(client.getAccount("119").allowsDebits())
                .as("a customer must not stay blocked after an admin unfreezes the account")
                .isTrue();
    }
}
