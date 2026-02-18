package com.suhasan.finance.transaction_service.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMock stubs for Account Service integration testing
 */
public class AccountServiceStubs {

    private static final String DEFAULT_OWNER_ID = "testuser";

    private final WireMockServer wireMockServer;
    private final ObjectMapper objectMapper;

    public AccountServiceStubs(WireMockServer wireMockServer, ObjectMapper objectMapper) {
        this.wireMockServer = wireMockServer;
        this.objectMapper = objectMapper;
        registerDefaultStubs();
    }

    private void registerDefaultStubs() {
        wireMockServer.stubFor(get(urlMatching("/api/accounts/.*"))
                .atPriority(10)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"ownerId\":\"" + DEFAULT_OWNER_ID + "\",\"accountType\":\"STANDARD\",\"balance\":10000.00,\"active\":true}")));

        wireMockServer.stubFor(post(urlMatching("/api/internal/accounts/.*/balance-ops"))
                .atPriority(10)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":1,\"operationId\":\"default-op\",\"applied\":true,\"newBalance\":10000.00,\"version\":1,\"status\":\"APPLIED\"}")));

        wireMockServer.stubFor(put(urlMatching("/api/accounts/.*/balance"))
                .atPriority(10)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"message\":\"Balance updated successfully\"}")));
    }

    /**
     * Stub successful account validation
     */
    public void stubAccountValidation(String accountId, boolean isActive) {
        try {
            AccountDto accountDto = AccountDto.builder()
                    .id(Long.valueOf(accountId.hashCode()))
                    .ownerId(DEFAULT_OWNER_ID)
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
        stubAccountWithTypeAndOwner(accountId, balance, "STANDARD", DEFAULT_OWNER_ID);
    }

    /**
     * Stub account with specific balance and account type
     */
    public void stubAccountWithType(String accountId, BigDecimal balance, String accountType) {
        stubAccountWithTypeAndOwner(accountId, balance, accountType, DEFAULT_OWNER_ID);
    }

    /**
     * Stub account with specific balance and owner.
     */
    public void stubAccountWithOwner(String accountId, BigDecimal balance, String ownerId) {
        stubAccountWithTypeAndOwner(accountId, balance, "STANDARD", ownerId);
    }

    /**
     * Stub account with specific balance, type and owner.
     */
    public void stubAccountWithTypeAndOwner(String accountId, BigDecimal balance, String accountType, String ownerId) {
        try {
            AccountDto accountDto = AccountDto.builder()
                    .id(Long.valueOf(accountId.hashCode()))
                    .ownerId(ownerId)
                    .accountType(accountType)
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

        wireMockServer.stubFor(post(urlEqualTo("/api/internal/accounts/" + accountId + "/balance-ops"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":" + Math.abs(accountId.hashCode())
                                + ",\"operationId\":\"test-op\",\"applied\":true,"
                                + "\"newBalance\":1000.00,\"version\":1,\"status\":\"APPLIED\"}")));
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

        wireMockServer.stubFor(post(urlEqualTo("/api/internal/accounts/" + accountId + "/balance-ops"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":" + Math.abs(accountId.hashCode())
                                + ",\"operationId\":\"test-op\",\"applied\":false,"
                                + "\"newBalance\":0.00,\"version\":1,\"status\":\"REJECTED\"}")));
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

        wireMockServer.stubFor(post(urlMatching("/api/internal/accounts/.*/balance-ops"))
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
                    .ownerId(DEFAULT_OWNER_ID)
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
        int internalCalls = wireMockServer.countRequestsMatching(
                postRequestedFor(urlEqualTo("/api/internal/accounts/" + accountId + "/balance-ops")).build()
        ).getCount();
        int legacyCalls = wireMockServer.countRequestsMatching(
                putRequestedFor(urlEqualTo("/api/accounts/" + accountId + "/balance")).build()
        ).getCount();

        if (internalCalls + legacyCalls <= 0) {
            wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/internal/accounts/" + accountId + "/balance-ops")));
        }
    }

    /**
     * Verify that balance update was called with specific amount
     */
    public void verifyBalanceUpdateCalledWithAmount(String accountId, BigDecimal amount) {
        int internalCallsWithAmount = wireMockServer.countRequestsMatching(
                postRequestedFor(urlEqualTo("/api/internal/accounts/" + accountId + "/balance-ops"))
                        .withRequestBody(containing(amount.stripTrailingZeros().toPlainString()))
                        .build()
        ).getCount();

        if (internalCallsWithAmount > 0) {
            return;
        }

        wireMockServer.verify(putRequestedFor(urlEqualTo("/api/accounts/" + accountId + "/balance"))
                .withRequestBody(containing(amount.toPlainString())));
    }
}
