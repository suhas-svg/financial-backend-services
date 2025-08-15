package com.suhasan.finance.transaction_service.e2e;

import com.suhasan.finance.transaction_service.dto.*;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.integration.AccountServiceStubs;
import com.suhasan.finance.transaction_service.integration.BaseIntegrationTest;
import com.suhasan.finance.transaction_service.integration.IntegrationTestConfiguration;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

import org.springframework.data.domain.Page;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-End workflow tests that simulate complete user transaction journeys
 * and validate the entire transaction processing pipeline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = {IntegrationTestConfiguration.class})
@DisplayName("Transaction Service E2E Workflow Tests")
class TransactionWorkflowE2ETest extends BaseIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IntegrationTestConfiguration.JwtTestUtil jwtTestUtil;

    private AccountServiceStubs accountServiceStubs;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUpWorkflowTest() {
        accountServiceStubs = new AccountServiceStubs(getWireMockServer(), objectMapper);
        userToken = jwtTestUtil.generateTokenWithRole("testuser", "USER");
        adminToken = jwtTestUtil.generateTokenWithRole("admin", "ADMIN");
        
        // Clear database before each test
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Complete User Journey: Account Setup to Multiple Transactions")
    void shouldCompleteFullUserTransactionJourney() {
        // Given: Set up accounts with initial balances
        String fromAccountId = "ACC001";
        String toAccountId = "ACC002";
        BigDecimal initialBalance = new BigDecimal("5000.00");
        
        accountServiceStubs.stubAccountWithBalance(fromAccountId, initialBalance);
        accountServiceStubs.stubAccountWithBalance(toAccountId, new BigDecimal("1000.00"));
        accountServiceStubs.stubBalanceUpdate(fromAccountId);
        accountServiceStubs.stubBalanceUpdate(toAccountId);

        // Step 1: User performs a deposit
        DepositRequest depositRequest = DepositRequest.builder()
                .accountId(fromAccountId)
                .amount(new BigDecimal("500.00"))
                .description("Initial deposit")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        HttpEntity<DepositRequest> depositEntity = new HttpEntity<>(depositRequest, headers);

        ResponseEntity<TransactionResponse> depositResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/deposit", depositEntity, TransactionResponse.class);

        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(depositResponse.getBody()).isNotNull();
        assertThat(depositResponse.getBody().getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(depositResponse.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // Step 2: User performs a transfer
        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(new BigDecimal("200.00"))
                .description("Transfer to friend")
                .build();

        HttpEntity<TransferRequest> transferEntity = new HttpEntity<>(transferRequest, headers);

        ResponseEntity<TransactionResponse> transferResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer", transferEntity, TransactionResponse.class);

        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transferResponse.getBody()).isNotNull();
        assertThat(transferResponse.getBody().getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(transferResponse.getBody().getFromAccountId()).isEqualTo(fromAccountId);
        assertThat(transferResponse.getBody().getToAccountId()).isEqualTo(toAccountId);

        // Step 3: User performs a withdrawal
        WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                .accountId(fromAccountId)
                .amount(new BigDecimal("100.00"))
                .description("ATM withdrawal")
                .build();

        HttpEntity<WithdrawalRequest> withdrawalEntity = new HttpEntity<>(withdrawalRequest, headers);

        ResponseEntity<TransactionResponse> withdrawalResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/withdraw", withdrawalEntity, TransactionResponse.class);

        assertThat(withdrawalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withdrawalResponse.getBody()).isNotNull();
        assertThat(withdrawalResponse.getBody().getType()).isEqualTo(TransactionType.WITHDRAWAL);

        // Step 4: User checks transaction history
        HttpEntity<Void> historyEntity = new HttpEntity<>(headers);

        ResponseEntity<Page<TransactionResponse>> historyResponse = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/account/" + fromAccountId + "?page=0&size=10",
                HttpMethod.GET, historyEntity, new ParameterizedTypeReference<Page<TransactionResponse>>() {});

        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyResponse.getBody()).isNotNull();
        assertThat(historyResponse.getBody().getContent()).hasSize(3);

        // Verify all Account Service interactions occurred
        accountServiceStubs.verifyAccountValidationCalled(fromAccountId);
        accountServiceStubs.verifyAccountValidationCalled(toAccountId);
        accountServiceStubs.verifyBalanceUpdateCalled(fromAccountId);
        accountServiceStubs.verifyBalanceUpdateCalled(toAccountId);
    }

    @Test
    @DisplayName("Transaction Limits Enforcement Workflow")
    void shouldEnforceTransactionLimitsInRealisticScenario() {
        // Given: Set up account with premium limits
        String accountId = "PREMIUM001";
        accountServiceStubs.stubPremiumAccount(accountId, new BigDecimal("50000.00"));
        accountServiceStubs.stubBalanceUpdate(accountId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);

        // Step 1: Perform multiple transactions within limits
        List<CompletableFuture<ResponseEntity<TransactionResponse>>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int i = 0; i < 5; i++) {
            WithdrawalRequest request = WithdrawalRequest.builder()
                    .accountId(accountId)
                    .amount(new BigDecimal("1000.00"))
                    .description("Withdrawal " + (i + 1))
                    .build();

            CompletableFuture<ResponseEntity<TransactionResponse>> future = CompletableFuture.supplyAsync(() -> {
                HttpEntity<WithdrawalRequest> entity = new HttpEntity<>(request, headers);
                return restTemplate.postForEntity(
                        getBaseUrl() + "/api/transactions/withdraw", entity, TransactionResponse.class);
            }, executor);

            futures.add(future);
        }

        // Wait for all transactions to complete
        List<ResponseEntity<TransactionResponse>> responses = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // All should succeed (within limits)
        responses.forEach(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        });

        // Step 2: Attempt transaction that exceeds daily limit
        WithdrawalRequest largeRequest = WithdrawalRequest.builder()
                .accountId(accountId)
                .amount(new BigDecimal("50000.00")) // Should exceed daily limit
                .description("Large withdrawal")
                .build();

        HttpEntity<WithdrawalRequest> largeEntity = new HttpEntity<>(largeRequest, headers);

        ResponseEntity<String> largeResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/withdraw", largeEntity, String.class);

        assertThat(largeResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(largeResponse.getBody()).contains("Transaction limit exceeded");

        executor.shutdown();
    }

    @Test
    @DisplayName("Error Handling and Recovery Scenarios")
    void shouldHandleErrorsAndRecoveryScenarios() {
        String accountId = "ERROR001";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);

        // Scenario 1: Account Service unavailable
        accountServiceStubs.stubAccountServiceUnavailable();

        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(accountId)
                .toAccountId("ACC002")
                .amount(new BigDecimal("100.00"))
                .description("Transfer during service outage")
                .build();

        HttpEntity<TransferRequest> transferEntity = new HttpEntity<>(transferRequest, headers);

        ResponseEntity<String> errorResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer", transferEntity, String.class);

        assertThat(errorResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(errorResponse.getBody()).contains("Account service unavailable");

        // Scenario 2: Account Service timeout
        accountServiceStubs.stubAccountServiceTimeout();

        ResponseEntity<String> timeoutResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer", transferEntity, String.class);

        assertThat(timeoutResponse.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);

        // Scenario 3: Recovery - Service becomes available
        accountServiceStubs.stubAccountWithBalance(accountId, new BigDecimal("1000.00"));
        accountServiceStubs.stubAccountWithBalance("ACC002", new BigDecimal("500.00"));
        accountServiceStubs.stubBalanceUpdate(accountId);
        accountServiceStubs.stubBalanceUpdate("ACC002");

        ResponseEntity<TransactionResponse> recoveryResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer", transferEntity, TransactionResponse.class);

        assertThat(recoveryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recoveryResponse.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Transaction Reversal Workflow")
    void shouldHandleTransactionReversalWorkflow() {
        // Given: Set up accounts and perform initial transaction
        String fromAccountId = "REV001";
        String toAccountId = "REV002";
        
        accountServiceStubs.stubAccountWithBalance(fromAccountId, new BigDecimal("2000.00"));
        accountServiceStubs.stubAccountWithBalance(toAccountId, new BigDecimal("1000.00"));
        accountServiceStubs.stubBalanceUpdate(fromAccountId);
        accountServiceStubs.stubBalanceUpdate(toAccountId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken); // Admin token for reversal

        // Step 1: Perform original transaction
        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(new BigDecimal("500.00"))
                .description("Original transfer")
                .build();

        HttpEntity<TransferRequest> transferEntity = new HttpEntity<>(transferRequest, headers);

        ResponseEntity<TransactionResponse> transferResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer", transferEntity, TransactionResponse.class);

        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String originalTransactionId = transferResponse.getBody().getTransactionId();

        // Step 2: Reverse the transaction
        HttpEntity<Void> reversalEntity = new HttpEntity<>(headers);

        ResponseEntity<TransactionResponse> reversalResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/" + originalTransactionId + "/reverse",
                reversalEntity, TransactionResponse.class);

        assertThat(reversalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reversalResponse.getBody().getType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(reversalResponse.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // Step 3: Attempt duplicate reversal (should fail)
        ResponseEntity<String> duplicateReversalResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/" + originalTransactionId + "/reverse",
                reversalEntity, String.class);

        assertThat(duplicateReversalResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateReversalResponse.getBody()).contains("already been reversed");

        // Step 4: Verify transaction history shows both original and reversal
        HttpEntity<Void> historyEntity = new HttpEntity<>(headers);

        ResponseEntity<Page<TransactionResponse>> historyResponse = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/account/" + fromAccountId,
                HttpMethod.GET, historyEntity, new ParameterizedTypeReference<Page<TransactionResponse>>() {});

        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyResponse.getBody().getContent()).hasSize(2);
        
        // Verify reversal is linked to original transaction
        boolean foundReversal = historyResponse.getBody().getContent().stream()
                .anyMatch(tx -> tx.getType() == TransactionType.REVERSAL && 
                               originalTransactionId.equals(tx.getReversalTransactionId()));
        assertThat(foundReversal).isTrue();
    }

    @Test
    @DisplayName("Concurrent Transaction Processing")
    void shouldHandleConcurrentTransactionProcessing() {
        // Given: Set up account with sufficient balance
        String accountId = "CONCURRENT001";
        accountServiceStubs.stubAccountWithBalance(accountId, new BigDecimal("10000.00"));
        accountServiceStubs.stubBalanceUpdate(accountId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);

        // Execute multiple concurrent transactions
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<ResponseEntity<TransactionResponse>>> futures = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            WithdrawalRequest request = WithdrawalRequest.builder()
                    .accountId(accountId)
                    .amount(new BigDecimal("50.00"))
                    .description("Concurrent withdrawal " + (i + 1))
                    .build();

            CompletableFuture<ResponseEntity<TransactionResponse>> future = CompletableFuture.supplyAsync(() -> {
                HttpEntity<WithdrawalRequest> entity = new HttpEntity<>(request, headers);
                return restTemplate.postForEntity(
                        getBaseUrl() + "/api/transactions/withdraw", entity, TransactionResponse.class);
            }, executor);

            futures.add(future);
        }

        // Wait for all transactions to complete
        List<ResponseEntity<TransactionResponse>> responses = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // Verify all transactions completed successfully
        long successfulTransactions = responses.stream()
                .mapToLong(response -> response.getStatusCode() == HttpStatus.OK ? 1 : 0)
                .sum();

        assertThat(successfulTransactions).isEqualTo(20);

        // Verify all transactions are recorded in database
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            long transactionCount = transactionRepository.count();
            assertThat(transactionCount).isEqualTo(20);
        });

        executor.shutdown();
    }

    @Test
    @DisplayName("Authentication and Authorization Workflow")
    void shouldEnforceAuthenticationAndAuthorization() {
        String accountId = "AUTH001";
        accountServiceStubs.stubAccountWithBalance(accountId, new BigDecimal("1000.00"));

        // Test 1: No token provided
        DepositRequest depositRequest = DepositRequest.builder()
                .accountId(accountId)
                .amount(new BigDecimal("100.00"))
                .description("Unauthorized deposit")
                .build();

        ResponseEntity<String> noTokenResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/deposit", depositRequest, String.class);

        assertThat(noTokenResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Test 2: Invalid token
        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.setBearerAuth("invalid.jwt.token");
        HttpEntity<DepositRequest> invalidTokenEntity = new HttpEntity<>(depositRequest, invalidHeaders);

        ResponseEntity<String> invalidTokenResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/deposit", invalidTokenEntity, String.class);

        assertThat(invalidTokenResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Test 3: Expired token
        String expiredToken = jwtTestUtil.generateExpiredToken("testuser");
        HttpHeaders expiredHeaders = new HttpHeaders();
        expiredHeaders.setBearerAuth(expiredToken);
        HttpEntity<DepositRequest> expiredTokenEntity = new HttpEntity<>(depositRequest, expiredHeaders);

        ResponseEntity<String> expiredTokenResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/deposit", expiredTokenEntity, String.class);

        assertThat(expiredTokenResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Test 4: Valid token - should succeed
        HttpHeaders validHeaders = new HttpHeaders();
        validHeaders.setBearerAuth(userToken);
        accountServiceStubs.stubBalanceUpdate(accountId);
        HttpEntity<DepositRequest> validTokenEntity = new HttpEntity<>(depositRequest, validHeaders);

        ResponseEntity<TransactionResponse> validTokenResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/deposit", validTokenEntity, TransactionResponse.class);

        assertThat(validTokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Data Consistency and Audit Trail Workflow")
    void shouldMaintainDataConsistencyAndAuditTrail() {
        // Given: Set up accounts
        String fromAccountId = "AUDIT001";
        String toAccountId = "AUDIT002";
        
        accountServiceStubs.stubAccountWithBalance(fromAccountId, new BigDecimal("2000.00"));
        accountServiceStubs.stubAccountWithBalance(toAccountId, new BigDecimal("1000.00"));
        accountServiceStubs.stubBalanceUpdate(fromAccountId);
        accountServiceStubs.stubBalanceUpdate(toAccountId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);

        // Perform a series of transactions
        List<String> transactionIds = new ArrayList<>();

        // Transaction 1: Transfer
        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(new BigDecimal("300.00"))
                .description("Audit test transfer")
                .build();

        HttpEntity<TransferRequest> transferEntity = new HttpEntity<>(transferRequest, headers);
        ResponseEntity<TransactionResponse> transferResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer", transferEntity, TransactionResponse.class);

        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        transactionIds.add(transferResponse.getBody().getTransactionId());

        // Transaction 2: Deposit
        DepositRequest depositRequest = DepositRequest.builder()
                .accountId(fromAccountId)
                .amount(new BigDecimal("500.00"))
                .description("Audit test deposit")
                .build();

        HttpEntity<DepositRequest> depositEntity = new HttpEntity<>(depositRequest, headers);
        ResponseEntity<TransactionResponse> depositResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/deposit", depositEntity, TransactionResponse.class);

        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        transactionIds.add(depositResponse.getBody().getTransactionId());

        // Verify all transactions are persisted with correct audit information
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            for (String transactionId : transactionIds) {
                HttpEntity<Void> getEntity = new HttpEntity<>(headers);
                ResponseEntity<TransactionResponse> getResponse = restTemplate.exchange(
                        getBaseUrl() + "/api/transactions/" + transactionId,
                        HttpMethod.GET, getEntity, TransactionResponse.class);

                assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                TransactionResponse transaction = getResponse.getBody();
                
                // Verify audit fields
                assertThat(transaction.getTransactionId()).isNotNull();
                assertThat(transaction.getCreatedAt()).isNotNull();
                assertThat(transaction.getCreatedBy()).isEqualTo("testuser");
                assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            }
        });

        // Verify transaction history maintains chronological order
        HttpEntity<Void> historyEntity = new HttpEntity<>(headers);
        ResponseEntity<Page<TransactionResponse>> historyResponse = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/account/" + fromAccountId + "?page=0&size=10&sort=createdAt,desc",
                HttpMethod.GET, historyEntity, new ParameterizedTypeReference<Page<TransactionResponse>>() {});

        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TransactionResponse> transactions = historyResponse.getBody().getContent();
        
        // Verify chronological order (most recent first)
        for (int i = 0; i < transactions.size() - 1; i++) {
            assertThat(transactions.get(i).getCreatedAt())
                    .isAfterOrEqualTo(transactions.get(i + 1).getCreatedAt());
        }
    }}
