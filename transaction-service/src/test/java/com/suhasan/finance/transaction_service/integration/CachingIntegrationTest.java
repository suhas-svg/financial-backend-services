package com.suhasan.finance.transaction_service.integration;

import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Redis caching functionality
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = {IntegrationTestConfiguration.class})
class CachingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IntegrationTestConfiguration.JwtTestUtil jwtTestUtil;

    private AccountServiceStubs accountServiceStubs;
    private String validJwtToken;

    @BeforeEach
    void setUpTest() {
        accountServiceStubs = new AccountServiceStubs(getWireMockServer(), objectMapper);
        validJwtToken = jwtTestUtil.generateToken("testuser");
        
        // Clear database and cache before each test
        transactionRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void shouldCacheTransactionHistoryResults() {
        // Given - Create test transactions
        String accountId = "account-001";
        createTestTransaction(accountId, "account-002", BigDecimal.valueOf(100.00), TransactionType.TRANSFER);
        createTestTransaction(null, accountId, BigDecimal.valueOf(500.00), TransactionType.DEPOSIT);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When - First request (should hit database and cache result)
        long startTime1 = System.currentTimeMillis();
        ResponseEntity<TransactionResponse[]> response1 = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/account/" + accountId,
                HttpMethod.GET,
                request,
                TransactionResponse[].class
        );
        long endTime1 = System.currentTimeMillis();

        // When - Second request (should hit cache)
        long startTime2 = System.currentTimeMillis();
        ResponseEntity<TransactionResponse[]> response2 = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/account/" + accountId,
                HttpMethod.GET,
                request,
                TransactionResponse[].class
        );
        long endTime2 = System.currentTimeMillis();

        // Then
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody()).hasSize(2);
        assertThat(response2.getBody()).hasSize(2);

        // Second request should be faster (cached)
        long firstRequestTime = endTime1 - startTime1;
        long secondRequestTime = endTime2 - startTime2;
        assertThat(secondRequestTime).isLessThan(firstRequestTime);

        // Verify cache keys exist
        Set<String> keys = redisTemplate.keys("transaction:history:*");
        assertThat(keys).isNotEmpty();
    }

    @Test
    void shouldEvictCacheWhenNewTransactionCreated() {
        // Given - Create initial transaction and cache it
        String accountId = "account-001";
        createTestTransaction(accountId, "account-002", BigDecimal.valueOf(100.00), TransactionType.TRANSFER);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);

        // First request to cache the result
        ResponseEntity<TransactionResponse[]> initialResponse = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/account/" + accountId,
                HttpMethod.GET,
                getRequest,
                TransactionResponse[].class
        );
        assertThat(initialResponse.getBody()).hasSize(1);

        // Verify cache exists
        Set<String> keysBeforeNewTransaction = redisTemplate.keys("transaction:history:*");
        assertThat(keysBeforeNewTransaction).isNotEmpty();

        // When - Create new transaction (should evict cache)
        accountServiceStubs.stubAccountWithBalance(accountId, BigDecimal.valueOf(1000.00));
        accountServiceStubs.stubBalanceUpdate(accountId);

        com.suhasan.finance.transaction_service.dto.DepositRequest depositRequest = 
            com.suhasan.finance.transaction_service.dto.DepositRequest.builder()
                .accountId(accountId)
                .amount(BigDecimal.valueOf(200.00))
                .description("Test deposit for cache eviction")
                .build();

        HttpEntity<com.suhasan.finance.transaction_service.dto.DepositRequest> depositRequestEntity = 
            new HttpEntity<>(depositRequest, headers);

        ResponseEntity<TransactionResponse> depositResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/deposit",
                depositRequestEntity,
                TransactionResponse.class
        );
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Then - Request transaction history again (should show updated results)
        ResponseEntity<TransactionResponse[]> updatedResponse = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/account/" + accountId,
                HttpMethod.GET,
                getRequest,
                TransactionResponse[].class
        );

        assertThat(updatedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updatedResponse.getBody()).hasSize(2); // Should now have 2 transactions
    }

    @Test
    void shouldCacheTransactionLimitsData() {
        // Given - Make a request that would trigger transaction limit checking
        String accountId = "account-001";
        BigDecimal largeAmount = BigDecimal.valueOf(4000.00); // Within limits but large enough to trigger checking

        accountServiceStubs.stubAccountWithBalance(accountId, BigDecimal.valueOf(10000.00));
        accountServiceStubs.stubBalanceUpdate(accountId);

        com.suhasan.finance.transaction_service.dto.WithdrawalRequest withdrawalRequest = 
            com.suhasan.finance.transaction_service.dto.WithdrawalRequest.builder()
                .accountId(accountId)
                .amount(largeAmount)
                .description("Test withdrawal for limit caching")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        HttpEntity<com.suhasan.finance.transaction_service.dto.WithdrawalRequest> request = 
            new HttpEntity<>(withdrawalRequest, headers);

        // When - First request (should cache transaction limits)
        ResponseEntity<TransactionResponse> response1 = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/withdraw",
                request,
                TransactionResponse.class
        );

        // When - Second similar request (should use cached limits)
        com.suhasan.finance.transaction_service.dto.WithdrawalRequest withdrawalRequest2 = 
            com.suhasan.finance.transaction_service.dto.WithdrawalRequest.builder()
                .accountId(accountId)
                .amount(BigDecimal.valueOf(1000.00))
                .description("Second test withdrawal")
                .build();

        HttpEntity<com.suhasan.finance.transaction_service.dto.WithdrawalRequest> request2 = 
            new HttpEntity<>(withdrawalRequest2, headers);

        ResponseEntity<TransactionResponse> response2 = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/withdraw",
                request2,
                TransactionResponse.class
        );

        // Then
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify cache keys for transaction limits exist
        Set<String> limitKeys = redisTemplate.keys("transaction:limits:*");
        assertThat(limitKeys).isNotEmpty();
    }

    @Test
    void shouldHandleRedisConnectionFailureGracefully() {
        // Given - Create test transaction
        String accountId = "account-001";
        createTestTransaction(accountId, "account-002", BigDecimal.valueOf(100.00), TransactionType.TRANSFER);

        // Simulate Redis connection failure by clearing connection
        try {
            redisTemplate.getConnectionFactory().getConnection().close();
        } catch (Exception e) {
            // Expected - connection might already be closed
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When - Request should still work without cache
        ResponseEntity<TransactionResponse[]> response = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/account/" + accountId,
                HttpMethod.GET,
                request,
                TransactionResponse[].class
        );

        // Then - Should still return data (from database)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void shouldCacheAccountValidationResults() {
        // Given
        String accountId = "account-001";
        accountServiceStubs.stubAccountWithBalance(accountId, BigDecimal.valueOf(1000.00));
        accountServiceStubs.stubBalanceUpdate(accountId);

        com.suhasan.finance.transaction_service.dto.DepositRequest depositRequest = 
            com.suhasan.finance.transaction_service.dto.DepositRequest.builder()
                .accountId(accountId)
                .amount(BigDecimal.valueOf(100.00))
                .description("Test deposit for account validation caching")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        HttpEntity<com.suhasan.finance.transaction_service.dto.DepositRequest> request = 
            new HttpEntity<>(depositRequest, headers);

        // When - Make multiple requests to same account
        ResponseEntity<TransactionResponse> response1 = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/deposit",
                request,
                TransactionResponse.class
        );

        ResponseEntity<TransactionResponse> response2 = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/deposit",
                request,
                TransactionResponse.class
        );

        // Then
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify account validation cache keys exist
        Set<String> accountKeys = redisTemplate.keys("account:validation:*");
        assertThat(accountKeys).isNotEmpty();

        // Verify Account Service was called (WireMock should show the calls)
        accountServiceStubs.verifyAccountValidationCalled(accountId);
    }

    private void createTestTransaction(String fromAccountId, String toAccountId, BigDecimal amount, TransactionType type) {
        Transaction transaction = Transaction.builder()
                .transactionId(java.util.UUID.randomUUID().toString())
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .currency("USD")
                .type(type)
                .status(TransactionStatus.COMPLETED)
                .description("Test transaction")
                .createdBy("testuser")
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        
        transactionRepository.save(transaction);
    }
}