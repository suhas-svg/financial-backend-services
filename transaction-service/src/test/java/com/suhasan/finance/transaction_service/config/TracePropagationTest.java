package com.suhasan.finance.transaction_service.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.web.client.HttpClientObservationsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;
import java.util.Arrays;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls to account-service must carry the caller's trace context, which only
 * happens when clients are built from Spring Boot's auto-configured
 * {@link WebClient.Builder} (it applies the observation customizer).
 */
class TracePropagationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ObservationAutoConfiguration.class,
                    OpenTelemetryAutoConfiguration.class,
                    OpenTelemetryTracingAutoConfiguration.class,
                    MicrometerTracingAutoConfiguration.class,
                    HttpClientObservationsAutoConfiguration.class,
                    CodecsAutoConfiguration.class,
                    WebClientAutoConfiguration.class))
            .withPropertyValues(
                    "management.tracing.sampling.probability=1.0",
                    "spring.codec.max-in-memory-size=1MB");

    private WireMockServer accountService;

    @BeforeEach
    void start() {
        accountService = new WireMockServer(wireMockConfig().dynamicPort());
        accountService.start();
    }

    @AfterEach
    void stop() {
        accountService.stop();
    }

    @Test
    void outgoingCallsCarryTheCallersTraceContext() {
        accountService.stubFor(get(urlEqualTo("/api/accounts/1")).willReturn(aResponse().withBody("{}")));

        runner.run(context -> {
            WebClient client = context.getBean(WebClient.Builder.class).baseUrl(accountService.baseUrl()).build();
            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            Tracer tracer = context.getBean(Tracer.class);

            String traceId = Observation.createNotStarted("transfer", registry).observe(() -> {
                client.get().uri("/api/accounts/1").retrieve().bodyToMono(String.class).block();
                return tracer.currentSpan().context().traceId();
            });

            accountService.verify(getRequestedFor(urlEqualTo("/api/accounts/1"))
                    .withHeader("traceparent", matching("00-" + traceId + "-[0-9a-f]{16}-01")));
        });
    }

    @Test
    void responsesUpToTheConfiguredCodecLimitAreAccepted() {
        String body = "x".repeat(600 * 1024); // above the 256 KB default, below 1 MB
        accountService.stubFor(get(urlEqualTo("/large")).willReturn(aResponse().withBody(body)));

        runner.run(context -> {
            WebClient client = context.getBean(WebClient.Builder.class).baseUrl(accountService.baseUrl()).build();
            assertThat(client.get().uri("/large").retrieve().bodyToMono(String.class).block()).hasSize(body.length());
        });
    }

    @Test
    void applicationDoesNotReplaceBootsWebClientBuilder() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Configuration.class));

        long overrides = scanner.findCandidateComponents("com.suhasan.finance.transaction_service").stream()
                .map(definition -> {
                    try {
                        return Class.forName(definition.getBeanClassName());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .map(Method::getReturnType)
                .filter(WebClient.Builder.class::isAssignableFrom)
                .count();

        assertThat(overrides)
                .as("a custom WebClient.Builder bean bypasses trace propagation and client metrics")
                .isZero();
    }
}
