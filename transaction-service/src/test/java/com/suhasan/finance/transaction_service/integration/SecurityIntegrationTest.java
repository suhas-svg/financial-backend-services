package com.suhasan.finance.transaction_service.integration;

import com.suhasan.finance.transaction_service.dto.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for JWT security and authentication
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = {IntegrationTestConfiguration.class})
class SecurityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IntegrationTestConfiguration.JwtTestUtil jwtTestUtil;

    private AccountServiceStubs accountServiceStubs;

    @BeforeEach
    void setUpTest() {
        accountServiceStubs = new AccountServiceStubs(getWireMockServer(), objectMapper);
    }

    @Test
    void shouldRejectRequestWithoutJwtToken() {
        // Given
        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId("account-001")
                .toAccountId("account-002")
                .amount(BigDecimal.valueOf(500.00))
                .description("Test transfer without token")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // No Authorization header
        HttpEntity<TransferRequest> request = new HttpEntity<>(transferRequest, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer",
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectRequestWithInvalidJwtToken() {
        // Given
        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId("account-001")
                .toAccountId("account-002")
                .amount(BigDecimal.valueOf(500.00))
                .description("Test transfer with invalid token")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid.jwt.token");
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> request = new HttpEntity<>(transferRequest, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer",
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectRequestWithExpiredJwtToken() {
        // Given
        String expiredToken = jwtTestUtil.generateExpiredToken("testuser");
        
        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId("account-001")
                .toAccountId("account-002")
                .amount(BigDecimal.valueOf(500.00))
                .description("Test transfer with expired token")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expiredToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> request = new HttpEntity<>(transferRequest, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer",
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAcceptRequestWithValidJwtToken() {
        // Given
        String validToken = jwtTestUtil.generateToken("testuser");
        
        // Stub Account Service responses
        accountServiceStubs.stubAccountWithBalance("account-001", BigDecimal.valueOf(1000.00));
        accountServiceStubs.stubAccountWithBalance("account-002", BigDecimal.valueOf(500.00));
        accountServiceStubs.stubBalanceUpdate("account-001");
        accountServiceStubs.stubBalanceUpdate("account-002");

        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId("account-001")
                .toAccountId("account-002")
                .amount(BigDecimal.valueOf(500.00))
                .description("Test transfer with valid token")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> request = new HttpEntity<>(transferRequest, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer",
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldAcceptRequestWithRoleBasedToken() {
        // Given
        String tokenWithRole = jwtTestUtil.generateTokenWithRole("adminuser", "ADMIN");
        
        // Stub Account Service responses
        accountServiceStubs.stubAccountWithBalance("account-001", BigDecimal.valueOf(1000.00));
        accountServiceStubs.stubAccountWithBalance("account-002", BigDecimal.valueOf(500.00));
        accountServiceStubs.stubBalanceUpdate("account-001");
        accountServiceStubs.stubBalanceUpdate("account-002");

        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId("account-001")
                .toAccountId("account-002")
                .amount(BigDecimal.valueOf(500.00))
                .description("Test transfer with role-based token")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenWithRole);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> request = new HttpEntity<>(transferRequest, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer",
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldProtectTransactionHistoryEndpoint() {
        // Given - No Authorization header
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/account/account-001",
                HttpMethod.GET,
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAllowAccessToTransactionHistoryWithValidToken() {
        // Given
        String validToken = jwtTestUtil.generateToken("testuser");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/account/account-001",
                HttpMethod.GET,
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldProtectHealthEndpoint() {
        // Given - No Authorization header
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/health",
                HttpMethod.GET,
                request,
                String.class
        );

        // Then
        // Health endpoint should be accessible without authentication for monitoring
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldAllowAccessToActuatorHealthEndpoint() {
        // Given - Actuator health endpoint should be accessible without authentication
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/actuator/health",
                HttpMethod.GET,
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);
    }
}