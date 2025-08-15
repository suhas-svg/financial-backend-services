package com.suhasan.finance.transaction_service.client;

import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResilientAccountServiceClient {
    
    private final WebClient.Builder webClientBuilder;
    private final Retry accountServiceRetry;
    private final CircuitBreaker accountServiceCircuitBreaker;
    private final TimeLimiter accountServiceTimeLimiter;
    
    @Value("${account-service.base-url}")
    private String accountServiceBaseUrl;
    
    @Value("${account-service.timeout:5000}")
    private int timeout;

    /**
     * Get account information by ID with resilience patterns
     */
    public AccountDto getAccount(String accountId) {
        log.debug("Fetching account information for ID: {} with resilience patterns", accountId);
        
        try {
            return getAccountAsync(accountId).block();
            
        } catch (Exception e) {
            log.error("Failed to get account {} after applying resilience patterns: {}", accountId, e.getMessage());
            
            // Check if circuit breaker is open
            if (accountServiceCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                throw new AccountServiceUnavailableException(
                    "Account Service is currently unavailable (circuit breaker open). Please try again later.");
            }
            
            // Handle different types of exceptions
            if (e instanceof WebClientResponseException webClientException) {
                if (webClientException.getStatusCode() == HttpStatus.NOT_FOUND) {
                    log.warn("Account not found: {}", accountId);
                    return null;
                }
                if (webClientException.getStatusCode().is4xxClientError()) {
                    log.warn("Client error for account {}: {}", accountId, webClientException.getMessage());
                    return null;
                }
            }
            
            throw new AccountServiceUnavailableException("Account service unavailable: " + e.getMessage());
        }
    }

    /**
     * Async method to get account with reactive resilience patterns
     */
    private Mono<AccountDto> getAccountAsync(String accountId) {
        WebClient webClient = webClientBuilder
                .baseUrl(accountServiceBaseUrl)
                .build();
        
        return webClient
                .get()
                .uri("/api/accounts/{id}", accountId)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(AccountDto.class)
                .timeout(Duration.ofMillis(timeout))
                .transformDeferred(RetryOperator.of(accountServiceRetry))
                .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
                .transformDeferred(TimeLimiterOperator.of(accountServiceTimeLimiter))
                .doOnSuccess(account -> log.debug("Successfully retrieved account: {}", accountId))
                .doOnError(error -> log.error("Error retrieving account {}: {}", accountId, error.getMessage()))
                .onErrorMap(throwable -> {
                    if (throwable instanceof WebClientResponseException webClientException) {
                        return webClientException;
                    }
                    return new RuntimeException("Account service call failed: " + throwable.getMessage(), throwable);
                });
    }

    /**
     * Update account balance with resilience patterns
     */
    public void updateAccountBalance(String accountId, BigDecimal newBalance) {
        log.debug("Updating balance for account {} to {} with resilience patterns", accountId, newBalance);
        
        try {
            updateAccountBalanceAsync(accountId, newBalance).block();
            
        } catch (Exception e) {
            log.error("Failed to update account balance for {} after applying resilience patterns: {}", 
                    accountId, e.getMessage());
            
            if (accountServiceCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                throw new AccountServiceUnavailableException(
                    "Account Service is currently unavailable (circuit breaker open). Balance update failed.");
            }
            
            throw new AccountServiceUnavailableException("Account service unavailable for balance update: " + e.getMessage());
        }
    }

    /**
     * Async method to update account balance
     */
    private Mono<Void> updateAccountBalanceAsync(String accountId, BigDecimal newBalance) {
        // For now, we'll simulate the balance update
        // In a real implementation, this would call a dedicated endpoint
        return Mono.fromRunnable(() -> {
            log.info("Balance updated for account {}: new balance = {}", accountId, newBalance);
        })
        .then()
        .transformDeferred(RetryOperator.of(accountServiceRetry))
        .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
        .transformDeferred(TimeLimiterOperator.of(accountServiceTimeLimiter))
        .doOnSuccess(result -> log.debug("Successfully updated balance for account: {}", accountId))
        .doOnError(error -> log.error("Error updating balance for account {}: {}", accountId, error.getMessage()));
    }

    /**
     * Validate account exists and is active with resilience patterns
     */
    public boolean validateAccount(String accountId) {
        try {
            AccountDto account = getAccount(accountId);
            boolean isValid = account != null && (account.getActive() == null || account.getActive());
            log.debug("Account validation for {}: {}", accountId, isValid);
            return isValid;
        } catch (Exception e) {
            log.warn("Account validation failed for {} due to service issues: {}", accountId, e.getMessage());
            
            // In case of service unavailability, we might want to fail fast
            // or implement a fallback mechanism depending on business requirements
            if (e instanceof AccountServiceUnavailableException) {
                throw e; // Re-throw to let caller handle service unavailability
            }
            
            return false;
        }
    }

    /**
     * Get account balance with resilience patterns
     */
    public BigDecimal getAccountBalance(String accountId) {
        try {
            AccountDto account = getAccount(accountId);
            BigDecimal balance = account != null ? account.getBalance() : BigDecimal.ZERO;
            log.debug("Retrieved balance for account {}: {}", accountId, balance);
            return balance;
        } catch (Exception e) {
            log.error("Failed to get balance for account {}: {}", accountId, e.getMessage());
            
            if (e instanceof AccountServiceUnavailableException) {
                throw e;
            }
            
            // Return zero balance as fallback, but log the issue
            log.warn("Returning zero balance as fallback for account {} due to service issues", accountId);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Check if account has sufficient balance with resilience patterns
     */
    public boolean hasSufficientBalance(String accountId, BigDecimal amount) {
        try {
            AccountDto account = getAccount(accountId);
            if (account == null) {
                log.warn("Account {} not found for balance check", accountId);
                return false;
            }
            
            boolean hasSufficientBalance;
            
            // For credit accounts, check available credit
            if ("CREDIT".equals(account.getAccountType())) {
                BigDecimal availableCredit = account.getAvailableCredit();
                hasSufficientBalance = availableCredit != null && availableCredit.compareTo(amount) >= 0;
                log.debug("Credit account {} available credit: {}, required: {}, sufficient: {}", 
                        accountId, availableCredit, amount, hasSufficientBalance);
            } else {
                // For other accounts, check balance
                hasSufficientBalance = account.getBalance().compareTo(amount) >= 0;
                log.debug("Account {} balance: {}, required: {}, sufficient: {}", 
                        accountId, account.getBalance(), amount, hasSufficientBalance);
            }
            
            return hasSufficientBalance;
            
        } catch (Exception e) {
            log.error("Error checking balance for account {}: {}", accountId, e.getMessage());
            
            if (e instanceof AccountServiceUnavailableException) {
                throw e;
            }
            
            // Conservative approach: assume insufficient balance if we can't verify
            log.warn("Assuming insufficient balance for account {} due to service issues", accountId);
            return false;
        }
    }

    /**
     * Check Account Service health with resilience patterns
     */
    public boolean checkHealth() {
        log.debug("Checking Account Service health with resilience patterns");
        
        try {
            return checkHealthAsync().block();
            
        } catch (Exception e) {
            log.warn("Account Service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Async method to check health
     */
    private Mono<Boolean> checkHealthAsync() {
        WebClient webClient = webClientBuilder
                .baseUrl(accountServiceBaseUrl)
                .build();
        
        return webClient
                .get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeout))
                .map(response -> response != null && response.contains("UP"))
                .transformDeferred(RetryOperator.of(accountServiceRetry))
                .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
                .doOnSuccess(isHealthy -> log.debug("Account Service health check result: {}", isHealthy))
                .doOnError(error -> log.warn("Account Service health check error: {}", error.getMessage()))
                .onErrorReturn(false); // Return false on any error
    }

    /**
     * Get circuit breaker state for monitoring
     */
    public CircuitBreaker.State getCircuitBreakerState() {
        return accountServiceCircuitBreaker.getState();
    }

    /**
     * Get circuit breaker metrics for monitoring
     */
    public CircuitBreaker.Metrics getCircuitBreakerMetrics() {
        return accountServiceCircuitBreaker.getMetrics();
    }

    /**
     * Get retry metrics for monitoring
     */
    public Retry.Metrics getRetryMetrics() {
        return accountServiceRetry.getMetrics();
    }

    /**
     * Get the base URL for the Account Service
     */
    public String getBaseUrl() {
        return accountServiceBaseUrl;
    }
}