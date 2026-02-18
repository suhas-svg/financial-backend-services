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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for transaction reversal functionality
 */
@SpringBootTest(
        classes = com.suhasan.finance.transaction_service.TransactionServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(classes = {IntegrationTestConfiguration.class})
@SuppressWarnings("null")
class TransactionReversalIntegrationTest extends BaseIntegrationTest {

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
    void shouldReverseTransferTransactionSuccessfully() {
        // Given - Create original transfer transaction
        String originalTransactionId = createOriginalTransferTransaction();
        
        // Stub Account Service for reversal
        accountServiceStubs.stubAccountWithBalance("account-001", BigDecimal.valueOf(500.00)); // After original transfer
        accountServiceStubs.stubAccountWithBalance("account-002", BigDecimal.valueOf(1500.00)); // After original transfer
        accountServiceStubs.stubBalanceUpdate("account-001"); // Will receive money back
        accountServiceStubs.stubBalanceUpdate("account-002"); // Will lose money

        ReversalRequest reversalRequest = ReversalRequest.builder()
                .reason("Customer dispute")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ReversalRequest> request = new HttpEntity<>(reversalRequest, headers);

        // When
        ResponseEntity<TransactionResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/" + originalTransactionId + "/reverse",
                request,
                TransactionResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(response.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));

        // Verify reversal transaction was created
        List<Transaction> allTransactions = transactionRepository.findAll();
        assertThat(allTransactions).hasSize(2); // Original + Reversal

        Transaction reversalTransaction = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.REVERSAL)
                .findFirst()
                .orElseThrow();

        assertThat(reversalTransaction.getFromAccountId()).isEqualTo("account-002"); // Reversed direction
        assertThat(reversalTransaction.getToAccountId()).isEqualTo("account-001");
        assertThat(reversalTransaction.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(reversalTransaction.getDescription()).contains("Customer dispute");

        // Verify original transaction is marked as reversed
        Transaction originalTransaction = transactionRepository.findById(originalTransactionId).orElseThrow();
        assertThat(originalTransaction.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(originalTransaction.getReversalTransactionId()).isEqualTo(reversalTransaction.getTransactionId());

        // Verify Account Service interactions
        accountServiceStubs.verifyBalanceUpdateCalled("account-001");
        accountServiceStubs.verifyBalanceUpdateCalled("account-002");
    }

    @Test
    void shouldReverseDepositTransactionSuccessfully() {
        // Given - Create original deposit transaction
        String originalTransactionId = createOriginalDepositTransaction();
        
        // Stub Account Service for reversal
        accountServiceStubs.stubAccountWithBalance("account-001", BigDecimal.valueOf(1500.00)); // After original deposit
        accountServiceStubs.stubBalanceUpdate("account-001"); // Will lose the deposited money

        ReversalRequest reversalRequest = ReversalRequest.builder()
                .reason("Fraudulent deposit")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ReversalRequest> request = new HttpEntity<>(reversalRequest, headers);

        // When
        ResponseEntity<TransactionResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/" + originalTransactionId + "/reverse",
                request,
                TransactionResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(response.getBody().getFromAccountId()).isEqualTo("account-001"); // Money taken from account
        assertThat(response.getBody().getToAccountId()).isEqualTo("EXTERNAL"); // External destination for deposit reversal

        // Verify reversal transaction was created
        List<Transaction> allTransactions = transactionRepository.findAll();
        assertThat(allTransactions).hasSize(2); // Original + Reversal

        // Verify original transaction is marked as reversed
        Transaction originalTransaction = transactionRepository.findById(originalTransactionId).orElseThrow();
        assertThat(originalTransaction.getStatus()).isEqualTo(TransactionStatus.REVERSED);
    }

    @Test
    void shouldReverseWithdrawalTransactionSuccessfully() {
        // Given - Create original withdrawal transaction
        String originalTransactionId = createOriginalWithdrawalTransaction();
        
        // Stub Account Service for reversal
        accountServiceStubs.stubAccountWithBalance("account-001", BigDecimal.valueOf(700.00)); // After original withdrawal
        accountServiceStubs.stubBalanceUpdate("account-001"); // Will receive money back

        ReversalRequest reversalRequest = ReversalRequest.builder()
                .reason("ATM malfunction")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ReversalRequest> request = new HttpEntity<>(reversalRequest, headers);

        // When
        ResponseEntity<TransactionResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/" + originalTransactionId + "/reverse",
                request,
                TransactionResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(response.getBody().getFromAccountId()).isEqualTo("EXTERNAL"); // External source for withdrawal reversal
        assertThat(response.getBody().getToAccountId()).isEqualTo("account-001"); // Money returned to account

        // Verify original transaction is marked as reversed
        Transaction originalTransaction = transactionRepository.findById(originalTransactionId).orElseThrow();
        assertThat(originalTransaction.getStatus()).isEqualTo(TransactionStatus.REVERSED);
    }

    @Test
    void shouldRejectReversalOfNonexistentTransaction() {
        // Given
        String nonexistentTransactionId = "nonexistent-transaction-id";
        
        ReversalRequest reversalRequest = ReversalRequest.builder()
                .reason("Test reversal")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ReversalRequest> request = new HttpEntity<>(reversalRequest, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/" + nonexistentTransactionId + "/reverse",
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        
        // Verify no reversal transaction was created
        List<Transaction> allTransactions = transactionRepository.findAll();
        assertThat(allTransactions).isEmpty();
    }

    @Test
    void shouldRejectDuplicateReversal() {
        // Given - Create original transaction and reverse it once
        String originalTransactionId = createOriginalTransferTransaction();
        
        // First reversal
        accountServiceStubs.stubAccountWithBalance("account-001", BigDecimal.valueOf(500.00));
        accountServiceStubs.stubAccountWithBalance("account-002", BigDecimal.valueOf(1500.00));
        accountServiceStubs.stubBalanceUpdate("account-001");
        accountServiceStubs.stubBalanceUpdate("account-002");

        ReversalRequest firstReversalRequest = ReversalRequest.builder()
                .reason("First reversal")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ReversalRequest> firstRequest = new HttpEntity<>(firstReversalRequest, headers);

        ResponseEntity<TransactionResponse> firstResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/" + originalTransactionId + "/reverse",
                firstRequest,
                TransactionResponse.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // When - Attempt second reversal
        ReversalRequest secondReversalRequest = ReversalRequest.builder()
                .reason("Duplicate reversal attempt")
                .build();

        HttpEntity<ReversalRequest> secondRequest = new HttpEntity<>(secondReversalRequest, headers);

        ResponseEntity<String> secondResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/" + originalTransactionId + "/reverse",
                secondRequest,
                String.class
        );

        // Then
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        
        // Verify only one reversal transaction exists
        List<Transaction> reversalTransactions = transactionRepository.findAll().stream()
                .filter(t -> t.getType() == TransactionType.REVERSAL)
                .toList();
        assertThat(reversalTransactions).hasSize(1);
    }

    @Test
    void shouldRejectReversalWhenAccountServiceUnavailable() {
        // Given
        String originalTransactionId = createOriginalTransferTransaction();
        
        // Stub Account Service as unavailable
        accountServiceStubs.stubAccountServiceUnavailable();

        ReversalRequest reversalRequest = ReversalRequest.builder()
                .reason("Test reversal with service unavailable")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ReversalRequest> request = new HttpEntity<>(reversalRequest, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/transactions/" + originalTransactionId + "/reverse",
                request,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        
        // Verify original transaction status is unchanged
        Transaction originalTransaction = transactionRepository.findById(originalTransactionId).orElseThrow();
        assertThat(originalTransaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        
        // Reversal intent is persisted and flagged for manual action on account-service outage.
        List<Transaction> reversalTransactions = transactionRepository.findAll().stream()
                .filter(t -> t.getType() == TransactionType.REVERSAL)
                .toList();
        assertThat(reversalTransactions).isNotEmpty();
        assertThat(reversalTransactions)
                .extracting(Transaction::getStatus)
                .contains(TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION);
    }

    private String createOriginalTransferTransaction() {
        Transaction transaction = Transaction.builder()
                .transactionId(java.util.UUID.randomUUID().toString())
                .fromAccountId("account-001")
                .toAccountId("account-002")
                .amount(BigDecimal.valueOf(500.00))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .description("Original transfer transaction")
                .createdBy("testuser")
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        
        return transactionRepository.save(transaction).getTransactionId();
    }

    private String createOriginalDepositTransaction() {
        Transaction transaction = Transaction.builder()
                .transactionId(java.util.UUID.randomUUID().toString())
                .fromAccountId("EXTERNAL")
                .toAccountId("account-001")
                .amount(BigDecimal.valueOf(500.00))
                .currency("USD")
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .description("Original deposit transaction")
                .createdBy("testuser")
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        
        return transactionRepository.save(transaction).getTransactionId();
    }

    private String createOriginalWithdrawalTransaction() {
        Transaction transaction = Transaction.builder()
                .transactionId(java.util.UUID.randomUUID().toString())
                .fromAccountId("account-001")
                .toAccountId("EXTERNAL")
                .amount(BigDecimal.valueOf(300.00))
                .currency("USD")
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.COMPLETED)
                .description("Original withdrawal transaction")
                .createdBy("testuser")
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        
        return transactionRepository.save(transaction).getTransactionId();
    }
}
