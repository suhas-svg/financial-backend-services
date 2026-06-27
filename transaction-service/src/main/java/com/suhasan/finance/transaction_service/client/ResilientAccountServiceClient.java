package com.suhasan.finance.transaction_service.client;

import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class ResilientAccountServiceClient {

    private final WebClient.Builder webClientBuilder;
    private final Retry accountServiceRetry;
    private final CircuitBreaker accountServiceCircuitBreaker;
    private final TimeLimiter accountServiceTimeLimiter;

    @Value("${account-service.base-url:http://localhost:8080}")
    private String accountServiceBaseUrl;

    @Value("${account-service.timeout:30000}")
    private int timeout;

    @Value("${security.jwt.internal-secret}")
    private String internalJwtSecret;

    /**
     * Get account information by ID with resilience patterns.
     * Uses the end-user JWT from security context for ownership-aware access.
     */
    @Cacheable(value = "account:validation", key = "#accountId")
    public AccountDto getAccount(String accountId) {
        log.debug("Fetching account information for ID: {}", accountId);
        try {
            return getAccountAsync(accountId).block();
        } catch (Exception e) {
            log.error("Failed to get account {}: {}", accountId, e.getMessage());
            if (accountServiceCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                throw new AccountServiceUnavailableException(
                        "Account Service is currently unavailable (circuit breaker open).");
            }
            if (e instanceof WebClientResponseException webClientException
                    && webClientException.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            if (e instanceof WebClientResponseException webClientException
                    && webClientException.getStatusCode().is4xxClientError()) {
                return null;
            }
            throw new AccountServiceUnavailableException("Account service unavailable: " + e.getMessage());
        }
    }

    private Mono<AccountDto> getAccountAsync(String accountId) {
        WebClient webClient = webClientBuilder.baseUrl(accountServiceBaseUrl).build();
        String jwtToken = getCurrentJwtToken();
        WebClient.RequestHeadersSpec<?> requestSpec = webClient.get()
                .uri("/api/accounts/{id}", accountId)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (jwtToken != null) {
            requestSpec = requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        }

        return requestSpec.retrieve()
                .bodyToMono(AccountDto.class)
                .timeout(Duration.ofMillis(timeout))
                .transformDeferred(RetryOperator.of(accountServiceRetry))
                .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
                .transformDeferred(TimeLimiterOperator.of(accountServiceTimeLimiter));
    }

    @CacheEvict(value = "account:validation", key = "#accountId")
    public void updateAccountBalance(String accountId, BigDecimal newBalance) {
        try {
            updateAccountBalanceAsync(accountId, newBalance).block();
        } catch (Exception e) {
            throw mapServiceException("balance update", e);
        }
    }

    private Mono<Void> updateAccountBalanceAsync(String accountId, BigDecimal newBalance) {
        String serviceToken = generateInternalServiceToken();
        return Mono.fromCallable(() -> {
                    WebClient webClient = webClientBuilder.baseUrl(accountServiceBaseUrl).build();
                    webClient.put()
                            .uri("/api/internal/accounts/{id}/balance", accountId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(new BalanceUpdateRequest(newBalance))
                            .retrieve()
                            .bodyToMono(Void.class)
                            .timeout(Duration.ofMillis(timeout))
                            .block();
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .transformDeferred(RetryOperator.of(accountServiceRetry))
                .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
                .transformDeferred(TimeLimiterOperator.of(accountServiceTimeLimiter));
    }

    @CacheEvict(value = "account:validation", key = "#accountId")
    public BalanceOperationResponse applyBalanceOperation(String accountId,
                                                          String operationId,
                                                          BigDecimal delta,
                                                          String transactionId,
                                                          String reason,
                                                          boolean allowNegative) {
        try {
            return applyBalanceOperationAsync(accountId, operationId, delta, transactionId, reason, allowNegative).block();
        } catch (Exception e) {
            throw mapServiceException("balance operation", e);
        }
    }

    private Mono<BalanceOperationResponse> applyBalanceOperationAsync(String accountId,
                                                                      String operationId,
                                                                      BigDecimal delta,
                                                                      String transactionId,
                                                                      String reason,
                                                                      boolean allowNegative) {
        String serviceToken = generateInternalServiceToken();
        BalanceOperationRequest request = new BalanceOperationRequest(
                operationId, delta, transactionId, reason, allowNegative);

        return Mono.fromCallable(() -> {
                    WebClient webClient = webClientBuilder.baseUrl(accountServiceBaseUrl).build();
                    return webClient.post()
                            .uri("/api/internal/accounts/{id}/balance-ops", accountId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(BalanceOperationResponse.class)
                            .timeout(Duration.ofMillis(timeout))
                            .block();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .transformDeferred(RetryOperator.of(accountServiceRetry))
                .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
                .transformDeferred(TimeLimiterOperator.of(accountServiceTimeLimiter));
    }

    @CacheEvict(value = "account:validation", key = "#accountId")
    public DebitHoldResponse placeDebitHold(String accountId,
                                            String holdId,
                                            BigDecimal amount,
                                            String transactionId,
                                            String reason) {
        try {
            return placeDebitHoldAsync(accountId, holdId, amount, transactionId, reason).block();
        } catch (Exception e) {
            throw mapServiceException("debit hold placement", e);
        }
    }

    private Mono<DebitHoldResponse> placeDebitHoldAsync(String accountId,
                                                        String holdId,
                                                        BigDecimal amount,
                                                        String transactionId,
                                                        String reason) {
        String serviceToken = generateInternalServiceToken();
        DebitHoldRequest request = new DebitHoldRequest(holdId, transactionId, amount, reason);
        return Mono.fromCallable(() -> {
                    WebClient webClient = webClientBuilder.baseUrl(accountServiceBaseUrl).build();
                    return webClient.post()
                            .uri("/api/internal/accounts/{id}/holds", accountId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(DebitHoldResponse.class)
                            .timeout(Duration.ofMillis(timeout))
                            .block();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .transformDeferred(RetryOperator.of(accountServiceRetry))
                .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
                .transformDeferred(TimeLimiterOperator.of(accountServiceTimeLimiter));
    }

    @CacheEvict(value = "account:validation", key = "#accountId")
    public DebitHoldResponse captureDebitHold(String accountId,
                                              String holdId,
                                              String transactionId,
                                              String reason) {
        try {
            return transitionDebitHoldAsync(accountId, holdId, transactionId, reason, true).block();
        } catch (Exception e) {
            throw mapServiceException("debit hold capture", e);
        }
    }

    @CacheEvict(value = "account:validation", key = "#accountId")
    public DebitHoldResponse releaseDebitHold(String accountId,
                                              String holdId,
                                              String transactionId,
                                              String reason) {
        try {
            return transitionDebitHoldAsync(accountId, holdId, transactionId, reason, false).block();
        } catch (Exception e) {
            throw mapServiceException("debit hold release", e);
        }
    }

    private Mono<DebitHoldResponse> transitionDebitHoldAsync(String accountId,
                                                             String holdId,
                                                             String transactionId,
                                                             String reason,
                                                             boolean capture) {
        String serviceToken = generateInternalServiceToken();
        HoldTransitionRequest request = new HoldTransitionRequest(transactionId, reason);
        String action = capture ? "capture" : "release";
        return Mono.fromCallable(() -> {
                    WebClient webClient = webClientBuilder.baseUrl(accountServiceBaseUrl).build();
                    return webClient.post()
                            .uri("/api/internal/accounts/{id}/holds/{holdId}/{action}", accountId, holdId, action)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(DebitHoldResponse.class)
                            .timeout(Duration.ofMillis(timeout))
                            .block();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .transformDeferred(RetryOperator.of(accountServiceRetry))
                .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
                .transformDeferred(TimeLimiterOperator.of(accountServiceTimeLimiter));
    }

    public boolean validateAccount(String accountId) {
        try {
            AccountDto account = getAccount(accountId);
            return account != null && (account.getActive() == null || account.getActive());
        } catch (Exception e) {
            if (e instanceof AccountServiceUnavailableException) {
                throw e;
            }
            return false;
        }
    }

    public BigDecimal getAccountBalance(String accountId) {
        AccountDto account = getAccount(accountId);
        return account != null ? account.getBalance() : BigDecimal.ZERO;
    }

    public boolean hasSufficientBalance(String accountId, BigDecimal amount) {
        AccountDto account = getAccount(accountId);
        if (account == null) {
            return false;
        }
        if ("CREDIT".equals(account.getAccountType())) {
            BigDecimal availableCredit = account.getAvailableCredit();
            return availableCredit != null && availableCredit.compareTo(amount) >= 0;
        }
        return account.spendableBalance().compareTo(amount) >= 0;
    }

    public boolean checkHealth() {
        try {
            return checkHealthAsync().block();
        } catch (Exception e) {
            return false;
        }
    }

    public void createNotification(NotificationRequest request) {
        String serviceToken = generateInternalServiceToken();
        try {
            WebClient webClient = webClientBuilder.baseUrl(accountServiceBaseUrl).build();
            webClient.post()
                    .uri("/api/internal/notifications")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
        } catch (Exception e) {
            throw mapServiceException("notification creation", e);
        }
    }

    @CacheEvict(value = "account:validation", key = "#accountId")
    public LedgerProjectionUpdateResponse applyLedgerProjection(
            String accountId,
            LedgerProjectionUpdateRequest request) {
        String serviceToken = generateInternalServiceToken();
        try {
            WebClient webClient = webClientBuilder.baseUrl(accountServiceBaseUrl).build();
            return webClient.put()
                    .uri("/api/internal/accounts/{id}/ledger-projection", accountId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(LedgerProjectionUpdateResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
        } catch (WebClientResponseException.Conflict conflict) {
            throw LedgerProjectionDeliveryException.terminal("projection conflict: " + conflict.getResponseBodyAsString());
        } catch (WebClientResponseException.BadRequest badRequest) {
            throw LedgerProjectionDeliveryException.terminal("projection rejected: " + badRequest.getResponseBodyAsString());
        } catch (Exception e) {
            throw mapServiceException("ledger projection update", e);
        }
    }

    private Mono<Boolean> checkHealthAsync() {
        WebClient webClient = webClientBuilder.baseUrl(accountServiceBaseUrl).build();
        return webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeout))
                .map(response -> response != null && response.contains("UP"))
                .transformDeferred(RetryOperator.of(accountServiceRetry))
                .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
                .onErrorReturn(false);
    }

    public CircuitBreaker.State getCircuitBreakerState() {
        return accountServiceCircuitBreaker.getState();
    }

    public CircuitBreaker.Metrics getCircuitBreakerMetrics() {
        return accountServiceCircuitBreaker.getMetrics();
    }

    public Retry.Metrics getRetryMetrics() {
        return accountServiceRetry.getMetrics();
    }

    public String getBaseUrl() {
        return accountServiceBaseUrl;
    }

    private String getCurrentJwtToken() {
        try {
            org.springframework.security.core.Authentication authentication =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() instanceof String) {
                return (String) authentication.getCredentials();
            }
        } catch (Exception ignored) {
            // no-op
        }
        return null;
    }

    private RuntimeException mapServiceException(String operation, Exception e) {
        log.error("Failed to execute {} via account-service: {}", operation, e.getMessage());
        if (accountServiceCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            return new AccountServiceUnavailableException(
                    "Account Service is currently unavailable (circuit breaker open).");
        }
        return new AccountServiceUnavailableException("Account service unavailable for " + operation + ": " + e.getMessage());
    }

    private String generateInternalServiceToken() {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(60);
        return Jwts.builder()
                .subject("transaction-service")
                // Keep aud as a plain string for compatibility with account-service JWT parser.
                .claim("aud", "account-service")
                .claim("roles", List.of("ROLE_INTERNAL_SERVICE"))
                .claim("token_type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(Keys.hmacShaKeyFor(internalJwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceUpdateRequest {
        private BigDecimal balance;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceOperationRequest {
        private String operationId;
        private BigDecimal delta;
        private String transactionId;
        private String reason;
        private boolean allowNegative;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceOperationResponse {
        private Long accountId;
        private String operationId;
        private boolean applied;
        private BigDecimal newBalance;
        private Long version;
        private String status;
        private String message;

        public BalanceOperationResponse(Long accountId, String operationId, boolean applied,
                                        BigDecimal newBalance, Long version, String status) {
            this(accountId, operationId, applied, newBalance, version, status, null);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerProjectionUpdateResponse {
        private Long id;
        private BigDecimal ledgerBalance;
        private BigDecimal pendingBalance;
        private BigDecimal availableBalance;
        private String currency;
        private Long ledgerProjectionVersion;
        private String ledgerProjectionSourceEventId;
    }

    public record LedgerProjectionUpdateRequest(
            BigDecimal postedBalance,
            BigDecimal pendingBalance,
            BigDecimal availableBalance,
            String currency,
            long version,
            String sourceEventId,
            LocalDateTime updatedAt) {
    }

    public static class LedgerProjectionDeliveryException extends RuntimeException {
        private final boolean terminal;

        private LedgerProjectionDeliveryException(String message, boolean terminal) {
            super(message);
            this.terminal = terminal;
        }

        public static LedgerProjectionDeliveryException terminal(String message) {
            return new LedgerProjectionDeliveryException(message, true);
        }

        public boolean terminal() {
            return terminal;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebitHoldRequest {
        private String holdId;
        private String transactionId;
        private BigDecimal amount;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldTransitionRequest {
        private String transactionId;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationRequest {
        private String userId;
        private String type;
        private String severity;
        private String title;
        private String message;
        private String sourceType;
        private String sourceId;
        private String dedupeKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebitHoldResponse {
        private String holdId;
        private Long accountId;
        private boolean applied;
        private BigDecimal ledgerBalance;
        private BigDecimal availableBalance;
        private Long version;
        private String status;
        private String message;

        public DebitHoldResponse(String holdId, Long accountId, boolean applied,
                                 BigDecimal ledgerBalance, BigDecimal availableBalance,
                                 String status, String message) {
            this(holdId, accountId, applied, ledgerBalance, availableBalance, null, status, message);
        }
    }
}
