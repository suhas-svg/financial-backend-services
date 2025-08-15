package com.suhasan.finance.transaction_service.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Configuration
@Slf4j
public class ResilienceConfig {

    @Value("${account-service.resilience.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${account-service.resilience.retry.wait-duration:1000}")
    private long retryWaitDuration;

    @Value("${account-service.resilience.circuit-breaker.failure-rate-threshold:50}")
    private float circuitBreakerFailureRateThreshold;

    @Value("${account-service.resilience.circuit-breaker.wait-duration-in-open-state:30000}")
    private long circuitBreakerWaitDurationInOpenState;

    @Value("${account-service.resilience.circuit-breaker.sliding-window-size:10}")
    private int circuitBreakerSlidingWindowSize;

    @Value("${account-service.resilience.circuit-breaker.minimum-number-of-calls:5}")
    private int circuitBreakerMinimumNumberOfCalls;

    @Value("${account-service.resilience.time-limiter.timeout:5000}")
    private long timeLimiterTimeout;

    /**
     * Retry configuration for Account Service calls
     */
    @Bean
    public Retry accountServiceRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(retryMaxAttempts)
                .waitDuration(Duration.ofMillis(retryWaitDuration))
                .retryOnException(throwable -> {
                    // Retry on network issues, timeouts, and 5xx errors
                    if (throwable instanceof TimeoutException) {
                        log.warn("Retrying due to timeout: {}", throwable.getMessage());
                        return true;
                    }
                    if (throwable instanceof WebClientResponseException webClientException) {
                        int statusCode = webClientException.getStatusCode().value();
                        boolean shouldRetry = statusCode >= 500 || statusCode == 429; // 5xx or rate limiting
                        if (shouldRetry) {
                            log.warn("Retrying due to HTTP {}: {}", statusCode, throwable.getMessage());
                        }
                        return shouldRetry;
                    }
                    if (throwable instanceof RuntimeException && 
                        (throwable.getMessage().contains("Connection refused") ||
                         throwable.getMessage().contains("Connection reset") ||
                         throwable.getMessage().contains("Read timeout"))) {
                        log.warn("Retrying due to connection issue: {}", throwable.getMessage());
                        return true;
                    }
                    return false;
                })
                .retryOnResult(result -> {
                    // Don't retry on successful results
                    return false;
                })
                .build();

        Retry retry = Retry.of("accountService", config);
        
        // Add event listeners for monitoring
        retry.getEventPublisher()
                .onRetry(event -> log.info("Account Service retry attempt {} for operation: {}", 
                        event.getNumberOfRetryAttempts(), event.getName()))
                .onSuccess(event -> log.debug("Account Service operation succeeded after {} retries", 
                        event.getNumberOfRetryAttempts()))
                .onError(event -> log.error("Account Service operation failed after {} retries: {}", 
                        event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));

        return retry;
    }

    /**
     * Circuit breaker configuration for Account Service calls
     */
    @Bean
    public CircuitBreaker accountServiceCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(circuitBreakerFailureRateThreshold)
                .waitDurationInOpenState(Duration.ofMillis(circuitBreakerWaitDurationInOpenState))
                .slidingWindowSize(circuitBreakerSlidingWindowSize)
                .minimumNumberOfCalls(circuitBreakerMinimumNumberOfCalls)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(throwable -> {
                    // Record failures for circuit breaker
                    if (throwable instanceof TimeoutException) {
                        return true;
                    }
                    if (throwable instanceof WebClientResponseException webClientException) {
                        int statusCode = webClientException.getStatusCode().value();
                        return statusCode >= 500; // Only 5xx errors should trigger circuit breaker
                    }
                    if (throwable instanceof RuntimeException && 
                        (throwable.getMessage().contains("Connection refused") ||
                         throwable.getMessage().contains("Connection reset"))) {
                        return true;
                    }
                    return false;
                })
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("accountService", config);
        
        // Add event listeners for monitoring
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> 
                        log.warn("Account Service circuit breaker state transition: {} -> {}", 
                                event.getStateTransition().getFromState(), 
                                event.getStateTransition().getToState()))
                .onCallNotPermitted(event -> 
                        log.warn("Account Service call not permitted due to circuit breaker in {} state", 
                                circuitBreaker.getState()))
                .onError(event -> 
                        log.error("Account Service circuit breaker recorded error: {}", 
                                event.getThrowable().getMessage()))
                .onSuccess(event -> 
                        log.debug("Account Service circuit breaker recorded success"));

        return circuitBreaker;
    }

    /**
     * Time limiter configuration for Account Service calls
     */
    @Bean
    public TimeLimiter accountServiceTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeLimiterTimeout))
                .cancelRunningFuture(true)
                .build();

        TimeLimiter timeLimiter = TimeLimiter.of("accountService", config);
        
        // Add event listeners for monitoring
        timeLimiter.getEventPublisher()
                .onTimeout(event -> 
                        log.warn("Account Service call timed out after {}ms", timeLimiterTimeout))
                .onSuccess(event -> 
                        log.debug("Account Service call completed within timeout"));

        return timeLimiter;
    }
}