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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for complete transaction workflows
 */
@SpringBootTest(
        classes = com.suhasan.finance.transaction_service.TransactionServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(classes = {IntegrationTestConfiguration.class})
@SuppressWarnings("null")
class TransactionIntegrationTest extends BaseIntegrationTest {

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
    void shouldProcessTransferSuccessfully() {
        // Given
        String fromAccountId = "account-001";
        String toAccountId = "account-002";
        BigDecimal transferAmount = BigDecimal.valueOf(500.00);

        // Stub Account Service responses
        accountServiceStubs.stubAccountWithBalance(fromAccountId, BigDecimal.valueOf(1000.00));
        accountServiceStubs.stubAccountWithBalance(toAccountId, BigDecimal.valueOf(500.00));
        accountServiceStubs.stubBalanceUpdate(fromAccountId);
        accountServiceStubs.stubBalanceUpdate(toAccountId);

        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(transferAmount)
                .description("Test transfer")
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
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(transferAmount);
        assertThat(response.getBody().getFromAccountId()).isEqualTo(fromAccountId);
        assertThat(response.getBody().getToAccountId()).isEqualTo(toAccountId);
        assertThat(response.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getBody().getType()).isEqualTo(TransactionType.TRANSFER);

        // Verify database record
        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        Transaction savedTransaction = transactions.get(0);
        assertThat(savedTransaction.getAmount()).isEqualByComparingTo(transferAmount);
        assertThat(savedTransaction.getFromAccountId()).isEqualTo(fromAccountId);
        assertThat(savedTransaction.getToAccountId()).isEqualTo(toAccountId);
        assertThat(savedTransaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // Verify Account Service interactions
        accountServiceStubs.verifyAccountValidationCalled(fromAccountId);
        accountServiceStubs.verifyBalanceUpdateCalled(fromAccountId);
        accountServiceStubs.verifyBalanceUpdateCalled(toAccountId);
    }

    @Test
    void shouldProcessDepositSuccessfully() {
        // Given
        String accountId = "account-001";
        BigDecimal depositAmount = BigDecimal.valueOf(1000.00);

        accountServiceStubs.stubAccountWithBalance(accountId, BigDecimal.valueOf(500.00));
        accountServiceStubs.stubBalanceUpdate(accountId);

        DepositRequest depositRequest = DepositRequest.builder()
                .accountId(accountId)
                .amount(depositAmount)
                .description("Test deposit")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<DepositRequest> request = new HttpEntity<>(depositRequest, headers);

        // When
        ResponseEntity<TransactionResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/deposit",
                request,
                TransactionResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(depositAmount);
        assertThat(response.getBody().getToAccountId()).isEqualTo(accountId);
        assertThat(response.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getBody().getType()).isEqualTo(TransactionType.DEPOSIT);

        // Verify database record
        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        Transaction savedTransaction = transactions.get(0);
        assertThat(savedTransaction.getAmount()).isEqualByComparingTo(depositAmount);
        assertThat(savedTransaction.getToAccountId()).isEqualTo(accountId);
        assertThat(savedTransaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void shouldProcessWithdrawalSuccessfully() {
        // Given
        String accountId = "account-001";
        BigDecimal withdrawalAmount = BigDecimal.valueOf(300.00);

        accountServiceStubs.stubAccountWithBalance(accountId, BigDecimal.valueOf(1000.00));
        accountServiceStubs.stubBalanceUpdate(accountId);

        WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                .accountId(accountId)
                .amount(withdrawalAmount)
                .description("Test withdrawal")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WithdrawalRequest> request = new HttpEntity<>(withdrawalRequest, headers);

        // When
        ResponseEntity<TransactionResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/withdraw",
                request,
                TransactionResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(withdrawalAmount);
        assertThat(response.getBody().getFromAccountId()).isEqualTo(accountId);
        assertThat(response.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getBody().getType()).isEqualTo(TransactionType.WITHDRAWAL);
    }

    @Test
    void shouldRejectTransferWhenInsufficientFunds() {
        // Given
        String fromAccountId = "account-001";
        String toAccountId = "account-002";
        BigDecimal transferAmount = BigDecimal.valueOf(1500.00);

        // Account has insufficient balance
        accountServiceStubs.stubAccountWithBalance(fromAccountId, BigDecimal.valueOf(1000.00));
        accountServiceStubs.stubAccountWithBalance(toAccountId, BigDecimal.valueOf(500.00));

        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(transferAmount)
                .description("Test transfer with insufficient funds")
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
        
        // Verify no transaction was created
        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).isEmpty();
    }

    @Test
    void shouldRejectTransactionWhenAccountNotFound() {
        // Given
        String fromAccountId = "nonexistent-account";
        String toAccountId = "account-002";
        BigDecimal transferAmount = BigDecimal.valueOf(500.00);

        accountServiceStubs.stubAccountNotFound(fromAccountId);
        accountServiceStubs.stubAccountWithBalance(toAccountId, BigDecimal.valueOf(500.00));

        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(transferAmount)
                .description("Test transfer with nonexistent account")
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
        
        // Verify no transaction was created
        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).isEmpty();
    }

    @Test
    void shouldHandleAccountServiceUnavailable() {
        // Given
        String fromAccountId = "account-001";
        String toAccountId = "account-002";
        BigDecimal transferAmount = BigDecimal.valueOf(500.00);

        accountServiceStubs.stubAccountServiceUnavailable();

        TransferRequest transferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(transferAmount)
                .description("Test transfer with service unavailable")
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
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        
        // Verify no transaction was created
        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).isEmpty();
    }

    @Test
    void shouldRetrieveTransactionHistory() {
        // Given - Create some test transactions
        String accountId = "account-001";
        createTestTransaction(accountId, "account-002", BigDecimal.valueOf(100.00), TransactionType.TRANSFER);
        createTestTransaction(null, accountId, BigDecimal.valueOf(500.00), TransactionType.DEPOSIT);
        createTestTransaction(accountId, null, BigDecimal.valueOf(50.00), TransactionType.WITHDRAWAL);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/api/transactions/account/" + accountId,
                HttpMethod.GET,
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("\"content\"");
        assertThat(transactionRepository.findAll()).hasSize(3);
    }

    private void createTestTransaction(String fromAccountId, String toAccountId, BigDecimal amount, TransactionType type) {
        Transaction transaction = Transaction.builder()
                .transactionId(java.util.UUID.randomUUID().toString())
                .fromAccountId(fromAccountId == null ? "EXTERNAL" : fromAccountId)
                .toAccountId(toAccountId == null ? "EXTERNAL" : toAccountId)
                .amount(amount)
                .currency("USD")
                .type(type)
                .status(TransactionStatus.COMPLETED)
                .description("Test transaction")
                .createdBy("testuser")
                .build();
        
        transactionRepository.save(transaction);
    }
}
