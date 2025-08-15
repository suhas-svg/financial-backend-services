package com.suhasan.finance.transaction_service.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMock stubs for Account Service integration testing
 */
public class AccountServiceStubs {

    private final WireMockServer wireMockServer;
    private final ObjectMapper objectMapper;

    public AccountServiceStubs(WireMockServer wireMockServer, ObjectMapper objectMapper) {
        this.wireMockServer = wireMockServer;
        this.objectMapper = objectMapper;
    }

    /**
     * Stub successful account validation
     */
    public void stubAccountValidation(String accountId, boolean isActive) {
        try {
            AccountDto accountDto = AccountDto.builder()
                    .id(Long.valueOf(accountId.hashCode()))
                    .ownerId("user-" + accountId)
                    .accountType("STANDARD")
                    .balance(BigDecimal.valueOf(10000.00))
                    .active(isActive)
                    .build();

            wireMockServer.stubFor(get(urlEqualTo("/api/accounts/" + accountId))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(objectMapper.writeValueAsString(accountDto))));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create account validation stub", e);
        }
    }

    /**
     * Stub account not found
     */
    public void stubAccountNotFound(String accountId) {
        wireMockServer.stubFor(get(urlEqualTo("/api/accounts/" + accountId))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Account not found\",\"accountId\":\"" + accountId + "\"}")));
    }

    /**
     * Stub account with specific balance
     */
    public void stubAccountWithBalance(String accountId, BigDecimal balance) {
        try {
            AccountDto accountDto = AccountDto.builder()
                    .id(Long.valueOf(accountId.hashCode()))
                    .ownerId("user-" + accountId)
                    .accountType("STANDARD")
                    .balance(balance)
                    .active(true)
                    .build();

            wireMockServer.stubFor(get(urlEqualTo("/api/accounts/" + accountId))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(objectMapper.writeValueAsString(accountDto))));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create account balance stub", e);
        }
    }

    /**
     * Stub successful balance update
     */
    public void stubBalanceUpdate(String accountId) {
        wireMockServer.stubFor(put(urlEqualTo("/api/accounts/" + accountId + "/balance"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"message\":\"Balance updated successfully\"}")));
    }

    /**
     * Stub balance update failure
     */
    public void stubBalanceUpdateFailure(String accountId) {
        wireMockServer.stubFor(put(urlEqualTo("/api/accounts/" + accountId + "/balance"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Insufficient funds\",\"accountId\":\"" + accountId + "\"}")));
    }

    /**
     * Stub Account Service unavailable
     */
    public void stubAccountServiceUnavailable() {
        wireMockServer.stubFor(get(urlMatching("/api/accounts/.*"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Service temporarily unavailable\"}")));

        wireMockServer.stubFor(put(urlMatching("/api/accounts/.*/balance"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Service temporarily unavailable\"}")));
    }

    /**
     * Stub Account Service timeout
     */
    public void stubAccountServiceTimeout() {
        wireMockServer.stubFor(get(urlMatching("/api/accounts/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(6000) // Longer than configured timeout
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));
    }

    /**
     * Stub premium account
     */
    public void stubPremiumAccount(String accountId, BigDecimal balance) {
        try {
            AccountDto accountDto = AccountDto.builder()
                    .id(Long.valueOf(accountId.hashCode()))
                    .ownerId("premium-user-" + accountId)
                    .accountType("PREMIUM")
                    .balance(balance)
                    .active(true)
                    .build();

            wireMockServer.stubFor(get(urlEqualTo("/api/accounts/" + accountId))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(objectMapper.writeValueAsString(accountDto))));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create premium account stub", e);
        }
    }

    /**
     * Verify that account validation was called
     */
    public void verifyAccountValidationCalled(String accountId) {
        wireMockServer.verify(getRequestedFor(urlEqualTo("/api/accounts/" + accountId)));
    }

    /**
     * Verify that balance update was called
     */
    public void verifyBalanceUpdateCalled(String accountId) {
        wireMockServer.verify(putRequestedFor(urlEqualTo("/api/accounts/" + accountId + "/balance")));
    }

    /**
     * Verify that balance update was called with specific amount
     */
    public void verifyBalanceUpdateCalledWithAmount(String accountId, BigDecimal amount) {
        wireMockServer.verify(putRequestedFor(urlEqualTo("/api/accounts/" + accountId + "/balance"))
                .withRequestBody(containing(amount.toString())));
    }
}