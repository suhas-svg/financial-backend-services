package com.suhasan.finance.transaction_service.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.dto.*;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full System Integration E2E Test
 * 
 * This test runs both Account Service and Transaction Service together
 * using Testcontainers to simulate a real production environment.
 * It tests the complete integration between both services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("e2e")
@DisplayName("Full System Integration E2E Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullSystemIntegrationE2ETest {

    @LocalServerPort
    private int transactionServicePort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // Shared network for all containers
    private static final Network network = Network.newNetwork();

    // PostgreSQL container shared by both services
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("fullsystem_test")
            .withUsername("testuser")
            .withPassword("testpass")
            .withInitScript("full-system-init.sql")
            .withNetwork(network)
            .withNetworkAliases("postgres-e2e");

    // Redis container for Transaction Service
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withNetwork(network)
            .withNetworkAliases("redis-e2e");

    // Account Service container
    @Container
    static GenericContainer<?> accountService = new GenericContainer<>("account-service:latest")
            .withExposedPorts(8080)
            .withNetwork(network)
            .withNetworkAliases("account-service-e2e")
            .withEnv("SPRING_PROFILES_ACTIVE", "e2e")
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres-e2e:5432/fullsystem_test")
            .withEnv("SPRING_DATASOURCE_USERNAME", "testuser")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "testpass")
            .withEnv("JWT_SECRET", "AY8Ro0HSBFyllm9ZPafT2GWuE/t8Yzq1P0Rf7bNeq14=")
            .withEnv("JWT_EXPIRATION", "3600000")
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(3))
            .dependsOn(postgres);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // Redis configuration
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        
        // Account Service configuration
        registry.add("account-service.base-url", () -> 
            "http://" + accountService.getHost() + ":" + accountService.getMappedPort(8080));
    }

    private String accountServiceUrl;
    private String transactionServiceUrl;
    private HttpHeaders authHeaders;

    @BeforeEach
    void setUp() {
        accountServiceUrl = "http://" + accountService.getHost() + ":" + accountService.getMappedPort(8080);
        transactionServiceUrl = "http://localhost:" + transactionServicePort;
        
        // Set up authentication headers
        authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        // Using a test JWT token - in production this would come from authentication service
        authHeaders.setBearerAuth("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJlMmUtdGVzdC11c2VyIiwicm9sZSI6IlVTRVIiLCJhdXRob3JpdGllcyI6IlJPTEVfVVNFUiIsImlhdCI6MTczMzQzMjQwMCwiZXhwIjoxNzMzNDM2MDAwfQ.example");
    }

    @Test
    @Order(1)
    @DisplayName("Should verify both services are running and healthy")
    void shouldVerifyServicesAreHealthy() {
        // Verify Account Service health
        ResponseEntity<String> accountHealth = restTemplate.getForEntity(
                accountServiceUrl + "/actuator/health", String.class);
        assertThat(accountHealth.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accountHealth.getBody()).contains("\"status\":\"UP\"");

        // Verify Transaction Service health
        ResponseEntity<String> transactionHealth = restTemplate.getForEntity(
                transactionServiceUrl + "/actuator/health", String.class);
        assertThat(transactionHealth.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transactionHealth.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @Order(2)
    @DisplayName("Should complete full user journey with real service integration")
    void shouldCompleteFullUserJourneyWithRealIntegration() {
        // Step 1: Create account in Account Service
        AccountCreateRequest accountRequest = AccountCreateRequest.builder()
                .ownerId("full-e2e-user")
                .accountType("STANDARD")
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        HttpEntity<AccountCreateRequest> accountEntity = new HttpEntity<>(accountRequest, authHeaders);
        ResponseEntity<AccountResponse> accountResponse = restTemplate.postForEntity(
                accountServiceUrl + "/api/accounts", accountEntity, AccountResponse.class);

        assertThat(accountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accountResponse.getBody()).isNotNull();
        
        Long accountId = accountResponse.getBody().getId();
        assertThat(accountId).isNotNull();
        assertThat(accountResponse.getBody().getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));

        // Step 2: Perform deposit via Transaction Service (which calls Account Service)
        DepositRequest depositRequest = DepositRequest.builder()
                .accountId(accountId.toString())
                .amount(new BigDecimal("500.00"))
                .description("Full E2E deposit test")
                .build();

        HttpEntity<DepositRequest> depositEntity = new HttpEntity<>(depositRequest, authHeaders);
        ResponseEntity<TransactionResponse> depositResponse = restTemplate.postForEntity(
                transactionServiceUrl + "/api/transactions/deposit", depositEntity, TransactionResponse.class);

        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(depositResponse.getBody()).isNotNull();
        assertThat(depositResponse.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(depositResponse.getBody().getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(depositResponse.getBody().getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));

        // Step 3: Verify account balance was updated in Account Service
        HttpEntity<Void> getAccountEntity = new HttpEntity<>(authHeaders);
        ResponseEntity<AccountResponse> updatedAccountResponse = restTemplate.exchange(
                accountServiceUrl + "/api/accounts/" + accountId, HttpMethod.GET, 
                getAccountEntity, AccountResponse.class);

        assertThat(updatedAccountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updatedAccountResponse.getBody().getBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));

        // Step 4: Create second account for transfer testing
        AccountCreateRequest secondAccountRequest = AccountCreateRequest.builder()
                .ownerId("full-e2e-user-2")
                .accountType("PREMIUM")
                .initialBalance(new BigDecimal("2000.00"))
                .build();

        HttpEntity<AccountCreateRequest> secondAccountEntity = new HttpEntity<>(secondAccountRequest, authHeaders);
        ResponseEntity<AccountResponse> secondAccountResponse = restTemplate.postForEntity(
                accountServiceUrl + "/api/accounts", secondAccountEntity, AccountResponse.class);

        Long secondAccountId = secondAccountResponse.getBody().getId();

        // Step 5: Perform transfer between accounts
        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(accountId.toString())
                .toAccountId(secondAccountId.toString())
                .amount(new BigDecimal("300.00"))
                .description("Full E2E transfer test")
                .build();

        HttpEntity<TransferRequest> transferEntity = new HttpEntity<>(transferRequest, authHeaders);
        ResponseEntity<TransactionResponse> transferResponse = restTemplate.postForEntity(
                transactionServiceUrl + "/api/transactions/transfer", transferEntity, TransactionResponse.class);

        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transferResponse.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transferResponse.getBody().getType()).isEqualTo(TransactionType.TRANSFER);

        // Step 6: Verify both account balances
        ResponseEntity<AccountResponse> finalAccount1 = restTemplate.exchange(
                accountServiceUrl + "/api/accounts/" + accountId, HttpMethod.GET, 
                getAccountEntity, AccountResponse.class);
        ResponseEntity<AccountResponse> finalAccount2 = restTemplate.exchange(
                accountServiceUrl + "/api/accounts/" + secondAccountId, HttpMethod.GET, 
                getAccountEntity, AccountResponse.class);

        assertThat(finalAccount1.getBody().getBalance()).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(finalAccount2.getBody().getBalance()).isEqualByComparingTo(new BigDecimal("2300.00"));

        // Step 7: Perform withdrawal
        WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                .accountId(accountId.toString())
                .amount(new BigDecimal("200.00"))
                .description("Full E2E withdrawal test")
                .build();

        HttpEntity<WithdrawalRequest> withdrawalEntity = new HttpEntity<>(withdrawalRequest, authHeaders);
        ResponseEntity<TransactionResponse> withdrawalResponse = restTemplate.postForEntity(
                transactionServiceUrl + "/api/transactions/withdraw", withdrawalEntity, TransactionResponse.class);

        assertThat(withdrawalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withdrawalResponse.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // Step 8: Verify transaction history
        ResponseEntity<Page<TransactionResponse>> historyResponse = restTemplate.exchange(
                transactionServiceUrl + "/api/transactions/account/" + accountId,
                HttpMethod.GET, getAccountEntity, new ParameterizedTypeReference<Page<TransactionResponse>>() {});

        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyResponse.getBody().getContent()).hasSize(3); // deposit, transfer, withdrawal

        // Step 9: Verify final account balance
        ResponseEntity<AccountResponse> veryFinalAccount = restTemplate.exchange(
                accountServiceUrl + "/api/accounts/" + accountId, HttpMethod.GET, 
                getAccountEntity, AccountResponse.class);

        assertThat(veryFinalAccount.getBody().getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @Order(3)
    @DisplayName("Should handle error scenarios with proper service integration")
    void shouldHandleErrorScenariosWithServiceIntegration() {
        // Test 1: Transaction with non-existent account
        DepositRequest invalidDepositRequest = DepositRequest.builder()
                .accountId("999999")
                .amount(new BigDecimal("100.00"))
                .description("Invalid account test")
                .build();

        HttpEntity<DepositRequest> invalidDepositEntity = new HttpEntity<>(invalidDepositRequest, authHeaders);
        ResponseEntity<String> invalidDepositResponse = restTemplate.postForEntity(
                transactionServiceUrl + "/api/transactions/deposit", invalidDepositEntity, String.class);

        assertThat(invalidDepositResponse.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);

        // Test 2: Insufficient funds scenario
        // Create account with low balance
        AccountCreateRequest poorAccountRequest = AccountCreateRequest.builder()
                .ownerId("poor-user")
                .accountType("STANDARD")
                .initialBalance(new BigDecimal("50.00"))
                .build();

        HttpEntity<AccountCreateRequest> poorAccountEntity = new HttpEntity<>(poorAccountRequest, authHeaders);
        ResponseEntity<AccountResponse> poorAccountResponse = restTemplate.postForEntity(
                accountServiceUrl + "/api/accounts", poorAccountEntity, AccountResponse.class);

        Long poorAccountId = poorAccountResponse.getBody().getId();

        // Attempt large withdrawal
        WithdrawalRequest largeWithdrawalRequest = WithdrawalRequest.builder()
                .accountId(poorAccountId.toString())
                .amount(new BigDecimal("1000.00"))
                .description("Insufficient funds test")
                .build();

        HttpEntity<WithdrawalRequest> largeWithdrawalEntity = new HttpEntity<>(largeWithdrawalRequest, authHeaders);
        ResponseEntity<String> largeWithdrawalResponse = restTemplate.postForEntity(
                transactionServiceUrl + "/api/transactions/withdraw", largeWithdrawalEntity, String.class);

        assertThat(largeWithdrawalResponse.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @Order(4)
    @DisplayName("Should handle concurrent operations with real service integration")
    void shouldHandleConcurrentOperationsWithRealIntegration() throws InterruptedException {
        // Create account for concurrent testing
        AccountCreateRequest concurrentAccountRequest = AccountCreateRequest.builder()
                .ownerId("concurrent-user")
                .accountType("PREMIUM")
                .initialBalance(new BigDecimal("10000.00"))
                .build();

        HttpEntity<AccountCreateRequest> concurrentAccountEntity = new HttpEntity<>(concurrentAccountRequest, authHeaders);
        ResponseEntity<AccountResponse> concurrentAccountResponse = restTemplate.postForEntity(
                accountServiceUrl + "/api/accounts", concurrentAccountEntity, AccountResponse.class);

        Long concurrentAccountId = concurrentAccountResponse.getBody().getId();

        // Perform multiple concurrent transactions
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<CompletableFuture<ResponseEntity<TransactionResponse>>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            DepositRequest concurrentDepositRequest = DepositRequest.builder()
                    .accountId(concurrentAccountId.toString())
                    .amount(new BigDecimal("50.00"))
                    .description("Concurrent deposit " + (i + 1))
                    .build();

            CompletableFuture<ResponseEntity<TransactionResponse>> future = CompletableFuture.supplyAsync(() -> {
                HttpEntity<DepositRequest> entity = new HttpEntity<>(concurrentDepositRequest, authHeaders);
                return restTemplate.postForEntity(
                        transactionServiceUrl + "/api/transactions/deposit", entity, TransactionResponse.class);
            }, executor);

            futures.add(future);
        }

        // Wait for all transactions to complete
        List<ResponseEntity<TransactionResponse>> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Verify results
        long successfulTransactions = results.stream()
                .mapToLong(response -> response.getStatusCode() == HttpStatus.OK ? 1 : 0)
                .sum();

        assertThat(successfulTransactions).isGreaterThanOrEqualTo(8); // Allow some failures due to concurrency

        // Verify final account balance
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            HttpEntity<Void> getAccountEntity = new HttpEntity<>(authHeaders);
            ResponseEntity<AccountResponse> finalAccountResponse = restTemplate.exchange(
                    accountServiceUrl + "/api/accounts/" + concurrentAccountId, HttpMethod.GET, 
                    getAccountEntity, AccountResponse.class);

            BigDecimal expectedBalance = new BigDecimal("10000.00").add(
                    new BigDecimal("50.00").multiply(new BigDecimal(successfulTransactions)));
            
            assertThat(finalAccountResponse.getBody().getBalance()).isEqualByComparingTo(expectedBalance);
        });
    }

    @Test
    @Order(5)
    @DisplayName("Should maintain data consistency across services")
    void shouldMaintainDataConsistencyAcrossServices() {
        // Create two accounts
        AccountCreateRequest account1Request = AccountCreateRequest.builder()
                .ownerId("consistency-user-1")
                .accountType("STANDARD")
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        AccountCreateRequest account2Request = AccountCreateRequest.builder()
                .ownerId("consistency-user-2")
                .accountType("STANDARD")
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        HttpEntity<AccountCreateRequest> account1Entity = new HttpEntity<>(account1Request, authHeaders);
        HttpEntity<AccountCreateRequest> account2Entity = new HttpEntity<>(account2Request, authHeaders);

        ResponseEntity<AccountResponse> account1Response = restTemplate.postForEntity(
                accountServiceUrl + "/api/accounts", account1Entity, AccountResponse.class);
        ResponseEntity<AccountResponse> account2Response = restTemplate.postForEntity(
                accountServiceUrl + "/api/accounts", account2Entity, AccountResponse.class);

        Long account1Id = account1Response.getBody().getId();
        Long account2Id = account2Response.getBody().getId();

        // Perform a series of transactions
        // 1. Deposit to account 1
        DepositRequest depositRequest = DepositRequest.builder()
                .accountId(account1Id.toString())
                .amount(new BigDecimal("200.00"))
                .description("Consistency test deposit")
                .build();

        HttpEntity<DepositRequest> depositEntity = new HttpEntity<>(depositRequest, authHeaders);
        ResponseEntity<TransactionResponse> depositResponse = restTemplate.postForEntity(
                transactionServiceUrl + "/api/transactions/deposit", depositEntity, TransactionResponse.class);

        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 2. Transfer from account 1 to account 2
        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(account1Id.toString())
                .toAccountId(account2Id.toString())
                .amount(new BigDecimal("150.00"))
                .description("Consistency test transfer")
                .build();

        HttpEntity<TransferRequest> transferEntity = new HttpEntity<>(transferRequest, authHeaders);
        ResponseEntity<TransactionResponse> transferResponse = restTemplate.postForEntity(
                transactionServiceUrl + "/api/transactions/transfer", transferEntity, TransactionResponse.class);

        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Withdraw from account 2
        WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                .accountId(account2Id.toString())
                .amount(new BigDecimal("100.00"))
                .description("Consistency test withdrawal")
                .build();

        HttpEntity<WithdrawalRequest> withdrawalEntity = new HttpEntity<>(withdrawalRequest, authHeaders);
        ResponseEntity<TransactionResponse> withdrawalResponse = restTemplate.postForEntity(
                transactionServiceUrl + "/api/transactions/withdraw", withdrawalEntity, TransactionResponse.class);

        assertThat(withdrawalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify final balances
        HttpEntity<Void> getAccountEntity = new HttpEntity<>(authHeaders);
        
        ResponseEntity<AccountResponse> finalAccount1 = restTemplate.exchange(
                accountServiceUrl + "/api/accounts/" + account1Id, HttpMethod.GET, 
                getAccountEntity, AccountResponse.class);
        ResponseEntity<AccountResponse> finalAccount2 = restTemplate.exchange(
                accountServiceUrl + "/api/accounts/" + account2Id, HttpMethod.GET, 
                getAccountEntity, AccountResponse.class);

        // Account 1: 1000 + 200 - 150 = 1050
        // Account 2: 1000 + 150 - 100 = 1050
        assertThat(finalAccount1.getBody().getBalance()).isEqualByComparingTo(new BigDecimal("1050.00"));
        assertThat(finalAccount2.getBody().getBalance()).isEqualByComparingTo(new BigDecimal("1050.00"));

        // Verify transaction history consistency
        ResponseEntity<Page<TransactionResponse>> history1 = restTemplate.exchange(
                transactionServiceUrl + "/api/transactions/account/" + account1Id,
                HttpMethod.GET, getAccountEntity, new ParameterizedTypeReference<Page<TransactionResponse>>() {});
        ResponseEntity<Page<TransactionResponse>> history2 = restTemplate.exchange(
                transactionServiceUrl + "/api/transactions/account/" + account2Id,
                HttpMethod.GET, getAccountEntity, new ParameterizedTypeReference<Page<TransactionResponse>>() {});

        assertThat(history1.getBody().getContent()).hasSize(2); // deposit and transfer out
        assertThat(history2.getBody().getContent()).hasSize(2); // transfer in and withdrawal
    }

    // Helper classes for Account Service DTOs
    public static class AccountCreateRequest {
        private String ownerId;
        private String accountType;
        private BigDecimal initialBalance;

        public static AccountCreateRequestBuilder builder() {
            return new AccountCreateRequestBuilder();
        }

        // Getters and setters
        public String getOwnerId() { return ownerId; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        public String getAccountType() { return accountType; }
        public void setAccountType(String accountType) { this.accountType = accountType; }
        public BigDecimal getInitialBalance() { return initialBalance; }
        public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance; }

        public static class AccountCreateRequestBuilder {
            private String ownerId;
            private String accountType;
            private BigDecimal initialBalance;

            public AccountCreateRequestBuilder ownerId(String ownerId) {
                this.ownerId = ownerId;
                return this;
            }

            public AccountCreateRequestBuilder accountType(String accountType) {
                this.accountType = accountType;
                return this;
            }

            public AccountCreateRequestBuilder initialBalance(BigDecimal initialBalance) {
                this.initialBalance = initialBalance;
                return this;
            }

            public AccountCreateRequest build() {
                AccountCreateRequest request = new AccountCreateRequest();
                request.setOwnerId(this.ownerId);
                request.setAccountType(this.accountType);
                request.setInitialBalance(this.initialBalance);
                return request;
            }
        }
    }

    public static class AccountResponse {
        private Long id;
        private String ownerId;
        private String accountType;
        private BigDecimal balance;
        private Boolean active;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOwnerId() { return ownerId; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        public String getAccountType() { return accountType; }
        public void setAccountType(String accountType) { this.accountType = accountType; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }
}