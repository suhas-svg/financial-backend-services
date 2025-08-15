package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.AccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.exception.TransactionAlreadyReversedException;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private AuditService auditService;

    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private TransferRequest transferRequest;
    private AccountDto fromAccount;
    private AccountDto toAccount;
    private Transaction transaction;
    private String userId;

    @BeforeEach
    void setUp() {
        userId = "user123";
        
        transferRequest = TransferRequest.builder()
                .fromAccountId("acc1")
                .toAccountId("acc2")
                .amount(BigDecimal.valueOf(100))
                .currency("USD")
                .description("Test transfer")
                .reference("REF123")
                .build();

        fromAccount = AccountDto.builder()
                .id(1L)
                .balance(BigDecimal.valueOf(1000))
                .accountType("CHECKING")
                .availableCredit(BigDecimal.ZERO)
                .build();

        toAccount = AccountDto.builder()
                .id(2L)
                .balance(BigDecimal.valueOf(500))
                .accountType("SAVINGS")
                .availableCredit(BigDecimal.ZERO)
                .build();

        transaction = Transaction.builder()
                .transactionId("txn123")
                .fromAccountId("acc1")
                .toAccountId("acc2")
                .amount(BigDecimal.valueOf(100))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .description("Test transfer")
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void processTransfer_Success() {
        // Arrange
        when(accountServiceClient.getAccount("acc1")).thenReturn(fromAccount);
        when(accountServiceClient.getAccount("acc2")).thenReturn(toAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

        // Act
        TransactionResponse result = transactionService.processTransfer(transferRequest, userId);

        // Assert
        assertNotNull(result);
        assertEquals("txn123", result.getTransactionId());
        assertEquals(TransactionType.TRANSFER, result.getType());
        assertEquals(TransactionStatus.COMPLETED, result.getStatus());

        verify(accountServiceClient).getAccount("acc1");
        verify(accountServiceClient).getAccount("acc2");
        verify(accountServiceClient).updateAccountBalance("acc1", BigDecimal.valueOf(900));
        verify(accountServiceClient).updateAccountBalance("acc2", BigDecimal.valueOf(600));
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(auditService).logTransactionInitiated(anyString(), eq(TransactionType.TRANSFER), 
                eq("acc1"), eq("acc2"), eq(BigDecimal.valueOf(100)), eq(userId));
        verify(metricsService).recordTransactionInitiated(TransactionType.TRANSFER);
    }

    @Test
    void processTransfer_FromAccountNotFound() {
        // Arrange
        when(accountServiceClient.getAccount("acc1")).thenReturn(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> transactionService.processTransfer(transferRequest, userId));
        
        assertEquals("From account not found", exception.getMessage());
        verify(auditService).logAccountValidation("acc1", userId, false, "Account not found");
        verify(metricsService).recordTransactionFailed(TransactionType.TRANSFER, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void processTransfer_ToAccountNotFound() {
        // Arrange
        when(accountServiceClient.getAccount("acc1")).thenReturn(fromAccount);
        when(accountServiceClient.getAccount("acc2")).thenReturn(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> transactionService.processTransfer(transferRequest, userId));
        
        assertEquals("To account not found", exception.getMessage());
        verify(auditService).logAccountValidation("acc2", userId, false, "Account not found");
        verify(metricsService).recordTransactionFailed(TransactionType.TRANSFER, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void processTransfer_InsufficientFunds() {
        // Arrange
        fromAccount.setBalance(BigDecimal.valueOf(50)); // Less than transfer amount
        when(accountServiceClient.getAccount("acc1")).thenReturn(fromAccount);
        when(accountServiceClient.getAccount("acc2")).thenReturn(toAccount);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> transactionService.processTransfer(transferRequest, userId));
        
        assertEquals("Insufficient funds", exception.getMessage());
        verify(auditService).logBalanceCheck("acc1", BigDecimal.valueOf(100), 
                BigDecimal.valueOf(50), false, userId);
        verify(metricsService).recordTransactionFailed(TransactionType.TRANSFER, "INSUFFICIENT_FUNDS");
    }

    @Test
    void processTransfer_TransactionLimitExceeded() {
        // Arrange
        transferRequest.setAmount(BigDecimal.valueOf(15000)); // Exceeds basic limit
        when(accountServiceClient.getAccount("acc1")).thenReturn(fromAccount);
        when(accountServiceClient.getAccount("acc2")).thenReturn(toAccount);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> transactionService.processTransfer(transferRequest, userId));
        
        assertEquals("Transaction exceeds limits", exception.getMessage());
        verify(metricsService).recordTransactionFailed(TransactionType.TRANSFER, "LIMIT_EXCEEDED");
    }

    @Test
    void processDeposit_Success() {
        // Arrange
        String accountId = "acc1";
        BigDecimal amount = BigDecimal.valueOf(200);
        String description = "Test deposit";
        
        when(accountServiceClient.getAccount(accountId)).thenReturn(fromAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

        // Act
        TransactionResponse result = transactionService.processDeposit(accountId, amount, description, userId);

        // Assert
        assertNotNull(result);
        verify(accountServiceClient).getAccount(accountId);
        verify(accountServiceClient).updateAccountBalance(accountId, BigDecimal.valueOf(1200));
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void processDeposit_AccountNotFound() {
        // Arrange
        String accountId = "acc1";
        BigDecimal amount = BigDecimal.valueOf(200);
        String description = "Test deposit";
        
        when(accountServiceClient.getAccount(accountId)).thenReturn(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> transactionService.processDeposit(accountId, amount, description, userId));
        
        assertEquals("Account not found", exception.getMessage());
    }

    @Test
    void processWithdrawal_Success() {
        // Arrange
        String accountId = "acc1";
        BigDecimal amount = BigDecimal.valueOf(200);
        String description = "Test withdrawal";
        
        when(accountServiceClient.getAccount(accountId)).thenReturn(fromAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

        // Act
        TransactionResponse result = transactionService.processWithdrawal(accountId, amount, description, userId);

        // Assert
        assertNotNull(result);
        verify(accountServiceClient).getAccount(accountId);
        verify(accountServiceClient).updateAccountBalance(accountId, BigDecimal.valueOf(800));
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void processWithdrawal_InsufficientFunds() {
        // Arrange
        String accountId = "acc1";
        BigDecimal amount = BigDecimal.valueOf(1500); // More than available balance
        String description = "Test withdrawal";
        
        when(accountServiceClient.getAccount(accountId)).thenReturn(fromAccount);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> transactionService.processWithdrawal(accountId, amount, description, userId));
        
        assertEquals("Insufficient funds", exception.getMessage());
    }

    @Test
    void getTransaction_Success() {
        // Arrange
        String transactionId = "txn123";
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        // Act
        TransactionResponse result = transactionService.getTransaction(transactionId);

        // Assert
        assertNotNull(result);
        assertEquals(transactionId, result.getTransactionId());
        verify(transactionRepository).findById(transactionId);
    }

    @Test
    void getTransaction_NotFound() {
        // Arrange
        String transactionId = "nonexistent";
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> transactionService.getTransaction(transactionId));
        
        assertEquals("Transaction not found: nonexistent", exception.getMessage());
    }

    @Test
    void getAccountTransactions_Success() {
        // Arrange
        String accountId = "acc1";
        Pageable pageable = PageRequest.of(0, 10);
        List<Transaction> transactions = Arrays.asList(transaction);
        Page<Transaction> page = new PageImpl<>(transactions, pageable, 1);
        
        when(transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                accountId, accountId, pageable)).thenReturn(page);

        // Act
        Page<TransactionResponse> result = transactionService.getAccountTransactions(accountId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("txn123", result.getContent().get(0).getTransactionId());
    }

    @Test
    void getUserTransactions_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Transaction> transactions = Arrays.asList(transaction);
        Page<Transaction> page = new PageImpl<>(transactions, pageable, 1);
        
        when(transactionRepository.findByCreatedByOrderByCreatedAtDesc(userId, pageable)).thenReturn(page);

        // Act
        Page<TransactionResponse> result = transactionService.getUserTransactions(userId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("txn123", result.getContent().get(0).getTransactionId());
    }

    @Test
    void reverseTransaction_Success() {
        // Arrange
        String transactionId = "txn123";
        String reason = "Customer request";
        Transaction completedTransaction = Transaction.builder()
                .transactionId(transactionId)
                .fromAccountId("acc1")
                .toAccountId("acc2")
                .amount(BigDecimal.valueOf(100))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
        
        Transaction reversalTransaction = Transaction.builder()
                .transactionId("rev123")
                .fromAccountId("acc2")
                .toAccountId("acc1")
                .amount(BigDecimal.valueOf(100))
                .currency("USD")
                .type(TransactionType.REVERSAL)
                .status(TransactionStatus.COMPLETED)
                .originalTransactionId(transactionId)
                .build();

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(completedTransaction));
        when(transactionRepository.isTransactionReversed(transactionId)).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(reversalTransaction);
        when(accountServiceClient.getAccount("acc2")).thenReturn(toAccount);
        when(accountServiceClient.getAccount("acc1")).thenReturn(fromAccount);

        // Act
        TransactionResponse result = transactionService.reverseTransaction(transactionId, reason, userId);

        // Assert
        assertNotNull(result);
        assertEquals(TransactionType.REVERSAL, result.getType());
        verify(transactionRepository).findById(transactionId);
        verify(transactionRepository).isTransactionReversed(transactionId);
        verify(auditService).logTransactionReversal(eq(transactionId), anyString(), eq(reason), eq(userId));
        verify(metricsService).recordTransactionReversal(TransactionType.TRANSFER);
    }

    @Test
    void reverseTransaction_AlreadyReversed() {
        // Arrange
        String transactionId = "txn123";
        String reason = "Customer request";
        Transaction completedTransaction = Transaction.builder()
                .transactionId(transactionId)
                .status(TransactionStatus.COMPLETED)
                .type(TransactionType.TRANSFER)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(completedTransaction));
        when(transactionRepository.isTransactionReversed(transactionId)).thenReturn(true);

        // Act & Assert
        TransactionAlreadyReversedException exception = assertThrows(TransactionAlreadyReversedException.class, 
                () -> transactionService.reverseTransaction(transactionId, reason, userId));
        
        assertTrue(exception.getMessage().contains("already been reversed"));
    }

    @Test
    void reverseTransaction_TransactionNotFound() {
        // Arrange
        String transactionId = "nonexistent";
        String reason = "Customer request";

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> transactionService.reverseTransaction(transactionId, reason, userId));
        
        assertEquals("Transaction not found: nonexistent", exception.getMessage());
    }

    @Test
    void reverseTransaction_CannotReverseReversalTransaction() {
        // Arrange
        String transactionId = "txn123";
        String reason = "Customer request";
        Transaction reversalTransaction = Transaction.builder()
                .transactionId(transactionId)
                .status(TransactionStatus.COMPLETED)
                .type(TransactionType.REVERSAL)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(reversalTransaction));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> transactionService.reverseTransaction(transactionId, reason, userId));
        
        assertEquals("Cannot reverse a reversal transaction", exception.getMessage());
    }

    @Test
    void reverseTransaction_TransactionTooOld() {
        // Arrange
        String transactionId = "txn123";
        String reason = "Customer request";
        Transaction oldTransaction = Transaction.builder()
                .transactionId(transactionId)
                .status(TransactionStatus.COMPLETED)
                .type(TransactionType.TRANSFER)
                .createdAt(LocalDateTime.now().minusDays(35)) // Older than 30 days
                .build();

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(oldTransaction));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> transactionService.reverseTransaction(transactionId, reason, userId));
        
        assertEquals("Cannot reverse transactions older than 30 days", exception.getMessage());
    }

    @Test
    void isTransactionReversed_True() {
        // Arrange
        String transactionId = "txn123";
        when(transactionRepository.isTransactionReversed(transactionId)).thenReturn(true);

        // Act
        boolean result = transactionService.isTransactionReversed(transactionId);

        // Assert
        assertTrue(result);
        verify(transactionRepository).isTransactionReversed(transactionId);
    }

    @Test
    void isTransactionReversed_False() {
        // Arrange
        String transactionId = "txn123";
        when(transactionRepository.isTransactionReversed(transactionId)).thenReturn(false);

        // Act
        boolean result = transactionService.isTransactionReversed(transactionId);

        // Assert
        assertFalse(result);
        verify(transactionRepository).isTransactionReversed(transactionId);
    }

    @Test
    void getReversalTransactions_Success() {
        // Arrange
        String originalTransactionId = "txn123";
        List<Transaction> reversals = Arrays.asList(transaction);
        
        when(transactionRepository.findReversalsByOriginalTransactionId(originalTransactionId))
                .thenReturn(reversals);

        // Act
        List<TransactionResponse> result = transactionService.getReversalTransactions(originalTransactionId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("txn123", result.get(0).getTransactionId());
    }

    @Test
    void getTransactionsByStatus_Success() {
        // Arrange
        TransactionStatus status = TransactionStatus.COMPLETED;
        List<Transaction> transactions = Arrays.asList(transaction);
        
        when(transactionRepository.findByStatusOrderByCreatedAtDesc(status)).thenReturn(transactions);

        // Act
        List<TransactionResponse> result = transactionService.getTransactionsByStatus(status);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("txn123", result.get(0).getTransactionId());
    }

    @Test
    void processPendingTransactions_Success() {
        // Arrange
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);
        Transaction pendingTransaction = Transaction.builder()
                .transactionId("pending123")
                .status(TransactionStatus.PENDING)
                .createdAt(cutoffTime.minusMinutes(10))
                .build();
        
        List<Transaction> pendingTransactions = Arrays.asList(pendingTransaction);
        when(transactionRepository.findPendingTransactionsOlderThan(any(LocalDateTime.class)))
                .thenReturn(pendingTransactions);

        // Act
        transactionService.processPendingTransactions();

        // Assert
        verify(transactionRepository).findPendingTransactionsOlderThan(any(LocalDateTime.class));
        verify(transactionRepository).save(pendingTransaction);
        assertEquals(TransactionStatus.FAILED, pendingTransaction.getStatus());
    }

    @Test
    void validateTransactionLimits_WithinLimit() {
        // Arrange
        String accountId = "acc1";
        String accountType = "CHECKING";
        TransactionType type = TransactionType.TRANSFER;
        BigDecimal amount = BigDecimal.valueOf(5000);

        // Act
        boolean result = transactionService.validateTransactionLimits(accountId, accountType, type, amount);

        // Assert
        assertTrue(result);
    }

    @Test
    void validateTransactionLimits_ExceedsLimit() {
        // Arrange
        String accountId = "acc1";
        String accountType = "CHECKING";
        TransactionType type = TransactionType.TRANSFER;
        BigDecimal amount = BigDecimal.valueOf(15000);

        // Act
        boolean result = transactionService.validateTransactionLimits(accountId, accountType, type, amount);

        // Assert
        assertFalse(result);
    }
}