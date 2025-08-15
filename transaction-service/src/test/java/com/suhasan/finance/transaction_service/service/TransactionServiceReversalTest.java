package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.AccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceReversalTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private Transaction originalTransaction;
    private AccountDto fromAccount;
    private AccountDto toAccount;

    @BeforeEach
    void setUp() {
        originalTransaction = Transaction.builder()
                .transactionId("original-tx-123")
                .fromAccountId("account-1")
                .toAccountId("account-2")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .description("Test transfer")
                .createdAt(LocalDateTime.now().minusHours(1))
                .createdBy("user-123")
                .build();

        fromAccount = AccountDto.builder()
                .id(2L) // Note: reversed for reversal
                .balance(new BigDecimal("500.00"))
                .accountType("CHECKING")
                .active(true)
                .build();

        toAccount = AccountDto.builder()
                .id(1L) // Note: reversed for reversal
                .balance(new BigDecimal("200.00"))
                .accountType("CHECKING")
                .active(true)
                .build();
    }

    @Test
    void testReverseTransaction_Success() {
        // Arrange
        when(transactionRepository.findById("original-tx-123"))
                .thenReturn(Optional.of(originalTransaction));
        when(transactionRepository.isTransactionReversed("original-tx-123"))
                .thenReturn(false);
        when(accountServiceClient.getAccount("account-2"))
                .thenReturn(fromAccount);
        when(accountServiceClient.getAccount("account-1"))
                .thenReturn(toAccount);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction tx = invocation.getArgument(0);
                    if (tx.getTransactionId() == null) {
                        tx.setTransactionId("reversal-tx-456");
                    }
                    return tx;
                });

        // Act
        TransactionResponse result = transactionService.reverseTransaction(
                "original-tx-123", "Customer request", "admin-user");

        // Assert
        assertNotNull(result);
        assertEquals(TransactionType.REVERSAL, result.getType());
        assertEquals("original-tx-123", result.getOriginalTransactionId());
        assertEquals("Customer request", result.getReversalReason());
        assertEquals("admin-user", result.getCreatedBy());
        assertEquals(new BigDecimal("100.00"), result.getAmount());
        assertEquals("account-2", result.getFromAccountId()); // Reversed
        assertEquals("account-1", result.getToAccountId()); // Reversed

        // Verify account balance updates were called
        verify(accountServiceClient).updateAccountBalance("account-2", new BigDecimal("400.00"));
        verify(accountServiceClient).updateAccountBalance("account-1", new BigDecimal("300.00"));

        // Verify transactions were saved
        verify(transactionRepository, times(3)).save(any(Transaction.class));
    }

    @Test
    void testReverseTransaction_AlreadyReversed() {
        // Arrange
        when(transactionRepository.findById("original-tx-123"))
                .thenReturn(Optional.of(originalTransaction));
        when(transactionRepository.isTransactionReversed("original-tx-123"))
                .thenReturn(true);

        // Act & Assert
        TransactionAlreadyReversedException exception = assertThrows(
                TransactionAlreadyReversedException.class,
                () -> transactionService.reverseTransaction("original-tx-123", "Test reason", "admin-user")
        );

        assertEquals("original-tx-123", exception.getTransactionId());
        assertTrue(exception.getMessage().contains("already been reversed"));

        // Verify no account updates were attempted
        verify(accountServiceClient, never()).updateAccountBalance(anyString(), any(BigDecimal.class));
    }

    @Test
    void testReverseTransaction_TransactionNotFound() {
        // Arrange
        when(transactionRepository.findById("non-existent-tx"))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.reverseTransaction("non-existent-tx", "Test reason", "admin-user")
        );

        assertTrue(exception.getMessage().contains("Transaction not found"));
    }

    @Test
    void testReverseTransaction_CannotReverseNonCompletedTransaction() {
        // Arrange
        originalTransaction.setStatus(TransactionStatus.PENDING);
        when(transactionRepository.findById("original-tx-123"))
                .thenReturn(Optional.of(originalTransaction));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.reverseTransaction("original-tx-123", "Test reason", "admin-user")
        );

        assertTrue(exception.getMessage().contains("Can only reverse completed transactions"));
    }

    @Test
    void testReverseTransaction_CannotReverseReversalTransaction() {
        // Arrange
        originalTransaction.setType(TransactionType.REVERSAL);
        when(transactionRepository.findById("original-tx-123"))
                .thenReturn(Optional.of(originalTransaction));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.reverseTransaction("original-tx-123", "Test reason", "admin-user")
        );

        assertTrue(exception.getMessage().contains("Cannot reverse a reversal transaction"));
    }

    @Test
    void testReverseTransaction_TooOldTransaction() {
        // Arrange
        originalTransaction.setCreatedAt(LocalDateTime.now().minusDays(35)); // Older than 30 days
        when(transactionRepository.findById("original-tx-123"))
                .thenReturn(Optional.of(originalTransaction));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.reverseTransaction("original-tx-123", "Test reason", "admin-user")
        );

        assertTrue(exception.getMessage().contains("Cannot reverse transactions older than 30 days"));
    }

    @Test
    void testIsTransactionReversed() {
        // Arrange
        when(transactionRepository.isTransactionReversed("tx-123")).thenReturn(true);
        when(transactionRepository.isTransactionReversed("tx-456")).thenReturn(false);

        // Act & Assert
        assertTrue(transactionService.isTransactionReversed("tx-123"));
        assertFalse(transactionService.isTransactionReversed("tx-456"));
    }
}