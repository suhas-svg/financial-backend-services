package com.suhasan.finance.transaction_service.sandbox;

import com.suhasan.finance.transaction_service.security.InternalServiceTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class SyntheticSandboxAccountClient {
    private final WebClient.Builder webClientBuilder;
    private final String baseUrl;
    private final InternalServiceTokens internalServiceTokens;

    public SyntheticSandboxAccountClient(WebClient.Builder webClientBuilder,
            @Value("${account-service.base-url:http://localhost:8080}") String baseUrl,
            InternalServiceTokens internalServiceTokens) {
        this.webClientBuilder = webClientBuilder;
        this.baseUrl = baseUrl;
        this.internalServiceTokens = internalServiceTokens;
    }

    public SeededAccounts seedAccounts(String owner) {
        return webClientBuilder.baseUrl(baseUrl).build().post()
                .uri(builder -> builder.path("/api/internal/sandbox/seed-accounts")
                        .queryParam("owner", owner).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken())
                .retrieve().bodyToMono(SeededAccounts.class).block();
    }

    private String serviceToken() {
        return internalServiceTokens.forAccountService();
    }

    public record SeededAccounts(String seedVersion, String zeroAccountId, String fundedAccountId,
                                 List<String> accountIds) {}
}
