package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.exception.AccountServiceUnavailableException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SpendingLimitReservationLifecycleClient {
    private final WebClient.Builder webClientBuilder;

    @Value("${account-service.base-url:http://localhost:8080}")
    private String accountServiceBaseUrl;

    @Value("${account-service.timeout:30000}")
    private int timeout;

    @Value("${security.jwt.internal-secret}")
    private String internalJwtSecret;

    public ReservationResponse reserve(String accountId, String operationType, BigDecimal amount,
                                       String idempotencyKey, String userId, String currency,
                                       String transactionCorrelation) {
        try {
            return webClient().post()
                    .uri("/api/internal/accounts/{id}/spending-limit-reservations", accountId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ReservationRequest(operationType, amount, idempotencyKey,
                            userId, currency, transactionCorrelation))
                    .retrieve()
                    .bodyToMono(ReservationResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
        } catch (WebClientResponseException.Conflict conflict) {
            throw new IllegalStateException(message(conflict, "Reservation payload conflict"), conflict);
        } catch (WebClientResponseException responseException) {
            if (responseException.getStatusCode().is4xxClientError()) {
                throw new IllegalArgumentException(message(responseException,
                        "Reservation request was rejected"), responseException);
            }
            throw unavailable("reserve spending limit", responseException);
        } catch (IllegalStateException | IllegalArgumentException known) {
            throw known;
        } catch (Exception error) {
            throw unavailable("reserve spending limit", error);
        }
    }

    public ReservationResponse lookup(String accountId, String operationType,
                                      String idempotencyKey, String userId) {
        try {
            return webClient().get()
                    .uri(builder -> builder
                            .path("/api/internal/accounts/{id}/spending-limit-reservations/{type}/{key}")
                            .queryParam("userId", userId)
                            .build(accountId, operationType, idempotencyKey))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken())
                    .retrieve()
                    .bodyToMono(ReservationResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
        } catch (WebClientResponseException.NotFound notFound) {
            return null;
        } catch (WebClientResponseException.Conflict conflict) {
            throw new IllegalStateException(message(conflict, "Reservation lookup conflict"), conflict);
        } catch (WebClientResponseException responseException) {
            if (responseException.getStatusCode().is4xxClientError()) {
                throw new IllegalArgumentException(message(responseException,
                        "Reservation lookup was rejected"), responseException);
            }
            throw unavailable("lookup spending limit reservation", responseException);
        } catch (IllegalStateException | IllegalArgumentException known) {
            throw known;
        } catch (Exception error) {
            throw unavailable("lookup spending limit reservation", error);
        }
    }

    public ReservationResponse transition(String accountId, Long reservationId, String action,
                                          String userId, String transactionCorrelation, String outcome) {
        try {
            return webClient().post()
                    .uri("/api/internal/accounts/{id}/spending-limit-reservations/{reservationId}/{action}",
                            accountId, reservationId, action)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new TransitionRequest(userId, transactionCorrelation, outcome))
                    .retrieve()
                    .bodyToMono(ReservationResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
        } catch (WebClientResponseException.Conflict conflict) {
            throw new IllegalStateException(message(conflict, "Reservation transition conflict"), conflict);
        } catch (WebClientResponseException responseException) {
            if (responseException.getStatusCode().is4xxClientError()) {
                throw new IllegalArgumentException(message(responseException,
                        "Reservation transition was rejected"), responseException);
            }
            throw unavailable("transition spending limit reservation", responseException);
        } catch (IllegalStateException | IllegalArgumentException known) {
            throw known;
        } catch (Exception error) {
            throw unavailable("transition spending limit reservation", error);
        }
    }

    private WebClient webClient() {
        return webClientBuilder.baseUrl(accountServiceBaseUrl).build();
    }

    private AccountServiceUnavailableException unavailable(String operation, Exception error) {
        return new AccountServiceUnavailableException(
                "Account service unavailable for " + operation + ": " + error.getMessage(), error);
    }

    private String message(WebClientResponseException error, String fallback) {
        String body = error.getResponseBodyAsString();
        return body == null || body.isBlank() ? fallback : body;
    }

    private String serviceToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("transaction-service")
                .claim("aud", "account-service")
                .claim("roles", List.of("ROLE_INTERNAL_SERVICE"))
                .claim("token_type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(internalJwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    public record ReservationRequest(
            String operationType,
            BigDecimal amount,
            String idempotencyKey,
            String userId,
            String currency,
            String transactionCorrelation) {
    }

    public record TransitionRequest(
            String userId,
            String transactionCorrelation,
            String outcome) {
    }

    public record ReservationResponse(
            boolean allowed,
            boolean replay,
            String currency,
            BigDecimal dailyLimit,
            BigDecimal dailyUsed,
            BigDecimal remaining,
            String reason,
            Long reservationId,
            String transactionCorrelation,
            BigDecimal amount,
            String fingerprint,
            String state,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime expiresAt,
            String outcome) {
    }
}
