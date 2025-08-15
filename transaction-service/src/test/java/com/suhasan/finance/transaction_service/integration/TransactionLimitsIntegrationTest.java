package com.suhasan.finance.transaction_service.integration;

import com.suhasan.finance.transaction_service.dto.*;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for transaction limits enforcement
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = {IntegrationTestConfiguration.class})
class TransactionLimitsIntegrationTest extends BaseIntegrationTest {

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
        
        // Clear database before each test
        transactionRepository.deleteAll();
    }

    @Test
    void shouldEnforcePerTransactionLimitForStandardAccount() {
        // Given - Standard account has per-transaction limit of 5000.00 for transfers
        String fromAccountId = "account-001";
        String toAccountId = "account-002";
        BigDecimal excessiveAmount = BigDecimal.valueOf(6000.00); // Exceeds per-transaction limit

        accountServiceStubs.stubAccountWithBalance(fromAccountId, BigDecimal.valueOf(10000.00));
        accountServiceStubs.stubAccountWithBalance(toAccountId, BigDecimal.valueOf(1000.00));

        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(excessiveAmount)
                .description("Transfer exceeding per-transaction limit")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> request = new HttpEntity<>(transferRequest, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer",
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Transaction limit exceeded");
        
        // Verify no transaction was created
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void shouldAllowTransactionWithinPerTransactionLimit() {
        // Given - Standard account with amount within per-transaction limit
        String fromAccountId = "account-001";
        String toAccountId = "account-002";
        BigDecimal validAmount = BigDecimal.valueOf(4000.00); // Within per-transaction limit of 5000.00

        accountServiceStubs.stubAccountWithBalance(fromAccountId, BigDecimal.valueOf(10000.00));
        accountServiceStubs.stubAccountWithBalance(toAccountId, BigDecimal.valueOf(1000.00));
        accountServiceStubs.stubBalanceUpdate(fromAccountId);
        accountServiceStubs.stubBalanceUpdate(toAccountId);

        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(validAmount)
                .description("Transfer within per-transaction limit")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> request = new HttpEntity<>(transferRequest, headers);

        // When
        ResponseEntity<TransactionResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer",
                request,
                TransactionResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(validAmount);
        
        // Verify transaction was created
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldEnforceDailyLimitForMultipleTransactions() {
        // Given - Create transactions that approach daily limit (10000.00 for standard transfers)
        String fromAccountId = "account-001";
        String toAccountId = "account-002";

        accountServiceStubs.stubAccountWithBalance(fromAccountId, BigDecimal.valueOf(20000.00));
        accountServiceStubs.stubAccountWithBalance(toAccountId, BigDecimal.valueOf(1000.00));
        accountServiceStubs.stubBalanceUpdate(fromAccountId);
        accountServiceStubs.stubBalanceUpdate(toAccountId);

        // Create transactions totaling 9000.00 (within daily limit)
        createTestTransaction(fromAccountId, toAccountId, BigDecimal.valueOf(4000.00), TransactionType.TRANSFER);
        createTestTransaction(fromAccountId, toAccountId, BigDecimal.valueOf(3000.00), TransactionType.TRANSFER);
        createTestTransaction(fromAccountId, toAccountId, BigDecimal.valueOf(2000.00), TransactionType.TRANSFER);

        // Attempt one more transaction that would exceed daily limit
        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(BigDecimal.valueOf(2000.00)) // Would make total 11000.00, exceeding 10000.00 limit
                .description("Transfer exceeding daily limit")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> request = new HttpEntity<>(transferRequest, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer",
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Daily transaction limit exceeded");
        
        // Verify only the original 3 transactions exist
        assertThat(transactionRepository.findAll()).hasSize(3);
    }

    @Test
    void shouldAllowHigherLimitsForPremiumAccount() {
        // Given - Premium account has higher limits (25000.00 per transaction, 50000.00 daily)
        String fromAccountId = "premium-account-001";
        String toAccountId = "premium-account-002";
        BigDecimal largeAmount = BigDecimal.valueOf(20000.00); // Would exceed standard limits but within premium

        accountServiceStubs.stubPremiumAccount(fromAccountId, BigDecimal.valueOf(50000.00));
        accountServiceStubs.stubPremiumAccount(toAccountId, BigDecimal.valueOf(10000.00));
        accountServiceStubs.stubBalanceUpdate(fromAccountId);
        accountServiceStubs.stubBalanceUpdate(toAccountId);

        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(largeAmount)
                .description("Large transfer for premium account")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> request = new HttpEntity<>(transferRequest, headers);

        // When
        ResponseEntity<TransactionResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer",
                request,
                TransactionResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(largeAmount);
        
        // Verify transaction was created
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldEnforceWithdrawalLimitsCorrectly() {
        // Given - Standard account withdrawal limits: 2500.00 per transaction, 5000.00 daily
        String accountId = "account-001";
        BigDecimal excessiveAmount = BigDecimal.valueOf(3000.00); // Exceeds per-transaction limit

        accountServiceStubs.stubAccountWithBalance(accountId, BigDecimal.valueOf(10000.00));

        WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                .accountId(accountId)
                .amount(excessiveAmount)
                .description("Withdrawal exceeding per-transaction limit")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WithdrawalRequest> request = new HttpEntity<>(withdrawalRequest, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/withdraw",
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Transaction limit exceeded");
        
        // Verify no transaction was created
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void shouldEnforceDepositLimitsCorrectly() {
        // Given - Standard account deposit limits: 10000.00 per transaction, 20000.00 daily
        String accountId = "account-001";
        BigDecimal excessiveAmount = BigDecimal.valueOf(15000.00); // Exceeds per-transaction limit

        accountServiceStubs.stubAccountWithBalance(accountId, BigDecimal.valueOf(5000.00));

        DepositRequest depositRequest = DepositRequest.builder()
                .accountId(accountId)
                .amount(excessiveAmount)
                .description("Deposit exceeding per-transaction limit")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<DepositRequest> request = new HttpEntity<>(depositRequest, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/deposit",
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Transaction limit exceeded");
        
        // Verify no transaction was created
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void shouldResetDailyLimitsForNewDay() {
        // Given - Create transactions for "yesterday" that used up daily limit
        String fromAccountId = "account-001";
        String toAccountId = "account-002";

        // Create transactions from yesterday totaling daily limit
        createTestTransactionForDate(fromAccountId, toAccountId, BigDecimal.valueOf(5000.00), 
                TransactionType.TRANSFER, LocalDateTime.now().minusDays(1));
        createTestTransactionForDate(fromAccountId, toAccountId, BigDecimal.valueOf(5000.00), 
                TransactionType.TRANSFER, LocalDateTime.now().minusDays(1));

        accountServiceStubs.stubAccountWithBalance(fromAccountId, BigDecimal.valueOf(10000.00));
        accountServiceStubs.stubAccountWithBalance(toAccountId, BigDecimal.valueOf(1000.00));
        accountServiceStubs.stubBalanceUpdate(fromAccountId);
        accountServiceStubs.stubBalanceUpdate(toAccountId);

        // Attempt new transaction today (should be allowed as it's a new day)
        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(BigDecimal.valueOf(3000.00))
                .description("Transfer on new day")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> request = new HttpEntity<>(transferRequest, headers);

        // When
        ResponseEntity<TransactionResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/transfer",
                request,
                TransactionResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000.00));
        
        // Verify new transaction was created (total should be 3)
        assertThat(transactionRepository.findAll()).hasSize(3);
    }

    private void createTestTransaction(String fromAccountId, String toAccountId, BigDecimal amount, TransactionType type) {
        createTestTransactionForDate(fromAccountId, toAccountId, amount, type, LocalDateTime.now());
    }

    private void createTestTransactionForDate(String fromAccountId, String toAccountId, BigDecimal amount, 
                                            TransactionType type, LocalDateTime dateTime) {
        Transaction transaction = Transaction.builder()
                .transactionId(java.util.UUID.randomUUID().toString())
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .currency("USD")
                .type(type)
                .status(TransactionStatus.COMPLETED)
                .description("Test transaction for limits")
                .createdBy("testuser")
                .createdAt(dateTime)
                .processedAt(dateTime)
                .build();
        
        transactionRepository.save(transaction);
    }
}