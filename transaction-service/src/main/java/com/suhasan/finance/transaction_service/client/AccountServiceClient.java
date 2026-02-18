package com.suhasan.finance.transaction_service.client;

import com.suhasan.finance.transaction_service.dto.AccountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class AccountServiceClient {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${account-service.base-url:http://localhost:8080}")
    private String accountServiceBaseUrl;
    
    @Value("${account-service.timeout:5000}")
    private int timeout;
    
    /**
     * Get account information by ID
     */
    public AccountDto getAccount(String accountId) {
        try {
            log.debug("Fetching account information for ID: {}", accountId);
            
            WebClient webClient = webClientBuilder
                    .baseUrl(accountServiceBaseUrl)
                    .build();
            
            AccountDto account = webClient
                    .get()
                    .uri("/api/accounts/{id}", accountId)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(AccountDto.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            log.debug("Successfully retrieved account: {}", accountId);
            return account;
            
        } catch (WebClientResponseException e) {
            log.error("Failed to get account {}: HTTP {} - {}", accountId, e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().is4xxClientError()) {
                return null; // Account not found
            }
            throw new RuntimeException("Failed to retrieve account: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error calling account service for account {}: {}", accountId, e.getMessage());
            throw new RuntimeException("Account service unavailable: " + e.getMessage());
        }
    }
    
    /**
     * Update account balance by calling the Account Service balance update endpoint
     */
    public void updateAccountBalance(String accountId, BigDecimal newBalance) {
        try {
            log.debug("Updating balance for account {} to {}", accountId, newBalance);
            
            WebClient webClient = webClientBuilder
                    .baseUrl(accountServiceBaseUrl)
                    .build();
            
            // Create balance update request
            BalanceUpdateRequest request = new BalanceUpdateRequest(newBalance);
            
            // Get current JWT token from security context
            String jwtToken = getCurrentJwtToken();
            
            WebClient.RequestHeadersSpec<?> requestSpec = webClient
                    .put()
                    .uri("/api/accounts/{id}/balance", accountId)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(request);
            
            // Add Authorization header if JWT token is available
            if (jwtToken != null) {
                requestSpec = requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
            }
            
            requestSpec
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            log.info("Successfully updated balance for account {}: new balance = {}", accountId, newBalance);
            
        } catch (WebClientResponseException e) {
            log.error("Failed to update account balance {}: HTTP {} - {}", accountId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to update account balance: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error updating account balance for {}: {}", accountId, e.getMessage());
            throw new RuntimeException("Account service unavailable: " + e.getMessage());
        }
    }
    
    /**
     * Get current JWT token from security context
     */
    private String getCurrentJwtToken() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() instanceof String) {
                // The JWT token is stored as credentials in our custom authentication
                return (String) authentication.getCredentials();
            }
        } catch (Exception e) {
            log.debug("Could not extract JWT token from security context: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Balance update request DTO
     */
    public static class BalanceUpdateRequest {
        private BigDecimal balance;
        
        public BalanceUpdateRequest() {}
        
        public BalanceUpdateRequest(BigDecimal balance) {
            this.balance = balance;
        }
        
        public BigDecimal getBalance() {
            return balance;
        }
        
        public void setBalance(BigDecimal balance) {
            this.balance = balance;
        }
    }
    
    /**
     * Validate account exists and is active
     */
    public boolean validateAccount(String accountId) {
        try {
            AccountDto account = getAccount(accountId);
            return account != null && (account.getActive() == null || account.getActive());
        } catch (Exception e) {
            log.warn("Account validation failed for {}: {}", accountId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get account balance
     */
    public BigDecimal getAccountBalance(String accountId) {
        AccountDto account = getAccount(accountId);
        return account != null ? account.getBalance() : BigDecimal.ZERO;
    }
    
    /**
     * Check if account has sufficient balance
     */
    public boolean hasSufficientBalance(String accountId, BigDecimal amount) {
        try {
            AccountDto account = getAccount(accountId);
            if (account == null) {
                return false;
            }
            
            // For credit accounts, check available credit
            if ("CREDIT".equals(account.getAccountType())) {
                BigDecimal availableCredit = account.getAvailableCredit();
                return availableCredit != null && availableCredit.compareTo(amount) >= 0;
            }
            
            // For other accounts, check balance
            return account.getBalance().compareTo(amount) >= 0;
            
        } catch (Exception e) {
            log.error("Error checking balance for account {}: {}", accountId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Check Account Service health
     */
    public boolean checkHealth() {
        try {
            log.debug("Checking Account Service health");
            
            WebClient webClient = webClientBuilder
                    .baseUrl(accountServiceBaseUrl)
                    .build();
            
            String response = webClient
                    .get()
                    .uri("/actuator/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            log.debug("Account Service health check successful");
            return response != null && response.contains("UP");
            
        } catch (Exception e) {
            log.warn("Account Service health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the base URL for the Account Service
     */
    public String getBaseUrl() {
        return accountServiceBaseUrl;
    }
    
    // Note: In a production system, balance updates would be handled through
    // dedicated endpoints or event-driven architecture
}
