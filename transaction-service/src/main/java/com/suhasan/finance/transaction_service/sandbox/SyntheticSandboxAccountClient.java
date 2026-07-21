package com.suhasan.finance.transaction_service.sandbox;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class SyntheticSandboxAccountClient {
    private final WebClient.Builder webClientBuilder;
    private final String baseUrl;
    private final String internalJwtSecret;

    public SyntheticSandboxAccountClient(WebClient.Builder webClientBuilder,
            @Value("${account-service.base-url:http://localhost:8080}") String baseUrl,
            @Value("${security.jwt.internal-secret}") String internalJwtSecret) {
        this.webClientBuilder = webClientBuilder;
        this.baseUrl = baseUrl;
        this.internalJwtSecret = internalJwtSecret;
    }

    public SeededAccounts seedAccounts(String owner) {
        return webClientBuilder.baseUrl(baseUrl).build().post()
                .uri(builder -> builder.path("/api/internal/sandbox/seed-accounts")
                        .queryParam("owner", owner).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken())
                .retrieve().bodyToMono(SeededAccounts.class).block();
    }

    private String serviceToken() {
        Instant now = Instant.now();
        return Jwts.builder().subject("transaction-service").claim("aud", "account-service")
                .claim("roles", List.of("ROLE_INTERNAL_SERVICE")).claim("token_type", "service")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(internalJwtSecret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    public record SeededAccounts(String seedVersion, String zeroAccountId, String fundedAccountId,
                                 List<String> accountIds) {}
}
