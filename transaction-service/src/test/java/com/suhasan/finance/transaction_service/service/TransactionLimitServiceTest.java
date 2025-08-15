package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.TransactionLimit;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionLimitRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionLimitServiceTest {

    @Mock
    private TransactionLimitRepository transactionLimitRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionLimitService transactionLimitService;

    private TransactionLimit transactionLimit;
    private String accountId;
    private String accountType;
    private TransactionType transactionType;

    @BeforeEach
    void setUp() {
        accountId = "acc123";
        accountType = "CHECKING";
        transactionType = TransactionType.TRANSFER;

        transactionLimit = TransactionLimit.builder()
                .id(1L)
                .accountType(accountType)
                .transactionType(transactionType)
                .dailyLimit(BigDecimal.valueOf(5000))
                .monthlyLimit(BigDecimal.valueOf(20000))
                .perTransactionLimit(BigDecimal.valueOf(2000))
                .dailyCount(10)
                .monthlyCount(50)
                .active(true)
                .build();
    }

    @Test
    void validateTransactionLimits_Success_WithinAllLimits() {
        // Arrange
        BigDecimal amount = BigDecimal.valueOf(1000);
        
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(transactionLimit));
        when(transactionRepository.getDailyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(2000));
        when(transactionRepository.getMonthlyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(8000));
        when(transactionRepository.getDailyTransactionCount(accountId, transactionType))
                .thenReturn(5L);
        when(transactionRepository.getMonthlyTransactionCount(accountId, transactionType))
                .thenReturn(25L);

        // Act
        boolean result = transactionLimitService.validateTransactionLimits(accountId, accountType, transactionType, amount);

        // Assert
        assertTrue(result);
        verify(transactionLimitRepository).findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType);
        verify(transactionRepository).getDailyTransactionSum(accountId, transactionType);
        verify(transactionRepository).getMonthlyTransactionSum(accountId, transactionType);
        verify(transactionRepository).getDailyTransactionCount(accountId, transactionType);
        verify(transactionRepository).getMonthlyTransactionCount(accountId, transactionType);
    }

    @Test
    void validateTransactionLimits_Success_NoLimitsConfigured() {
        // Arrange
        BigDecimal amount = BigDecimal.valueOf(1000);
        
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.empty());

        // Act
        boolean result = transactionLimitService.validateTransactionLimits(accountId, accountType, transactionType, amount);

        // Assert
        assertTrue(result);
        verify(transactionLimitRepository).findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void validateTransactionLimits_Fail_ExceedsPerTransactionLimit() {
        // Arrange
        BigDecimal amount = BigDecimal.valueOf(3000); // Exceeds per-transaction limit of 2000
        
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(transactionLimit));

        // Act
        boolean result = transactionLimitService.validateTransactionLimits(accountId, accountType, transactionType, amount);

        // Assert
        assertFalse(result);
        verify(transactionLimitRepository).findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void validateTransactionLimits_Fail_ExceedsDailyAmountLimit() {
        // Arrange
        BigDecimal amount = BigDecimal.valueOf(1000);
        
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(transactionLimit));
        when(transactionRepository.getDailyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(4500)); // Current daily sum + new amount would exceed 5000

        // Act
        boolean result = transactionLimitService.validateTransactionLimits(accountId, accountType, transactionType, amount);

        // Assert
        assertFalse(result);
        verify(transactionRepository).getDailyTransactionSum(accountId, transactionType);
    }

    @Test
    void validateTransactionLimits_Fail_ExceedsDailyCountLimit() {
        // Arrange
        BigDecimal amount = BigDecimal.valueOf(1000);
        
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(transactionLimit));
        when(transactionRepository.getDailyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(2000));
        when(transactionRepository.getDailyTransactionCount(accountId, transactionType))
                .thenReturn(10L); // Already at daily count limit

        // Act
        boolean result = transactionLimitService.validateTransactionLimits(accountId, accountType, transactionType, amount);

        // Assert
        assertFalse(result);
        verify(transactionRepository).getDailyTransactionCount(accountId, transactionType);
    }

    @Test
    void validateTransactionLimits_Fail_ExceedsMonthlyAmountLimit() {
        // Arrange
        BigDecimal amount = BigDecimal.valueOf(1000);
        
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(transactionLimit));
        when(transactionRepository.getDailyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(2000));
        when(transactionRepository.getMonthlyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(19500)); // Current monthly sum + new amount would exceed 20000
        when(transactionRepository.getDailyTransactionCount(accountId, transactionType))
                .thenReturn(5L);

        // Act
        boolean result = transactionLimitService.validateTransactionLimits(accountId, accountType, transactionType, amount);

        // Assert
        assertFalse(result);
        verify(transactionRepository).getMonthlyTransactionSum(accountId, transactionType);
    }

    @Test
    void validateTransactionLimits_Fail_ExceedsMonthlyCountLimit() {
        // Arrange
        BigDecimal amount = BigDecimal.valueOf(1000);
        
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(transactionLimit));
        when(transactionRepository.getDailyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(2000));
        when(transactionRepository.getMonthlyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(8000));
        when(transactionRepository.getDailyTransactionCount(accountId, transactionType))
                .thenReturn(5L);
        when(transactionRepository.getMonthlyTransactionCount(accountId, transactionType))
                .thenReturn(50L); // Already at monthly count limit

        // Act
        boolean result = transactionLimitService.validateTransactionLimits(accountId, accountType, transactionType, amount);

        // Assert
        assertFalse(result);
        verify(transactionRepository).getMonthlyTransactionCount(accountId, transactionType);
    }

    @Test
    void validateTransactionLimits_HandleException() {
        // Arrange
        BigDecimal amount = BigDecimal.valueOf(1000);
        
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        boolean result = transactionLimitService.validateTransactionLimits(accountId, accountType, transactionType, amount);

        // Assert
        assertFalse(result); // Should fail safe
        verify(transactionLimitRepository).findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType);
    }

    @Test
    void getTransactionLimit_Found() {
        // Arrange
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(transactionLimit));

        // Act
        TransactionLimit result = transactionLimitService.getTransactionLimit(accountType, transactionType);

        // Assert
        assertNotNull(result);
        assertEquals(transactionLimit.getId(), result.getId());
        assertEquals(accountType, result.getAccountType());
        assertEquals(transactionType, result.getTransactionType());
        verify(transactionLimitRepository).findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType);
    }

    @Test
    void getTransactionLimit_NotFound() {
        // Arrange
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.empty());

        // Act
        TransactionLimit result = transactionLimitService.getTransactionLimit(accountType, transactionType);

        // Assert
        assertNull(result);
        verify(transactionLimitRepository).findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType);
    }

    @Test
    void saveTransactionLimit_Success() {
        // Arrange
        when(transactionLimitRepository.save(transactionLimit)).thenReturn(transactionLimit);

        // Act
        TransactionLimit result = transactionLimitService.saveTransactionLimit(transactionLimit);

        // Assert
        assertNotNull(result);
        assertEquals(transactionLimit.getId(), result.getId());
        verify(transactionLimitRepository).save(transactionLimit);
    }

    @Test
    void getRemainingDailyLimit_WithLimit() {
        // Arrange
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(transactionLimit));
        when(transactionRepository.getDailyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(2000));

        // Act
        BigDecimal result = transactionLimitService.getRemainingDailyLimit(accountId, accountType, transactionType);

        // Assert
        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(3000), result); // 5000 - 2000
        verify(transactionRepository).getDailyTransactionSum(accountId, transactionType);
    }

    @Test
    void getRemainingDailyLimit_NoLimit() {
        // Arrange
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.empty());

        // Act
        BigDecimal result = transactionLimitService.getRemainingDailyLimit(accountId, accountType, transactionType);

        // Assert
        assertNull(result);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getRemainingDailyLimit_LimitExceeded() {
        // Arrange
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(transactionLimit));
        when(transactionRepository.getDailyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(6000)); // Exceeds daily limit

        // Act
        BigDecimal result = transactionLimitService.getRemainingDailyLimit(accountId, accountType, transactionType);

        // Assert
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void getRemainingMonthlyLimit_WithLimit() {
        // Arrange
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(transactionLimit));
        when(transactionRepository.getMonthlyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(8000));

        // Act
        BigDecimal result = transactionLimitService.getRemainingMonthlyLimit(accountId, accountType, transactionType);

        // Assert
        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(12000), result); // 20000 - 8000
        verify(transactionRepository).getMonthlyTransactionSum(accountId, transactionType);
    }

    @Test
    void getRemainingMonthlyLimit_NoLimit() {
        // Arrange
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.empty());

        // Act
        BigDecimal result = transactionLimitService.getRemainingMonthlyLimit(accountId, accountType, transactionType);

        // Assert
        assertNull(result);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getRemainingMonthlyLimit_LimitExceeded() {
        // Arrange
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(transactionLimit));
        when(transactionRepository.getMonthlyTransactionSum(accountId, transactionType))
                .thenReturn(BigDecimal.valueOf(25000)); // Exceeds monthly limit

        // Act
        BigDecimal result = transactionLimitService.getRemainingMonthlyLimit(accountId, accountType, transactionType);

        // Assert
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void validateTransactionLimits_WithNullLimits() {
        // Arrange
        BigDecimal amount = BigDecimal.valueOf(1000);
        TransactionLimit limitWithNulls = TransactionLimit.builder()
                .id(1L)
                .accountType(accountType)
                .transactionType(transactionType)
                .dailyLimit(null)
                .monthlyLimit(null)
                .perTransactionLimit(null)
                .dailyCount(null)
                .monthlyCount(null)
                .active(true)
                .build();
        
        when(transactionLimitRepository.findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType))
                .thenReturn(Optional.of(limitWithNulls));

        // Act
        boolean result = transactionLimitService.validateTransactionLimits(accountId, accountType, transactionType, amount);

        // Assert
        assertTrue(result); // Should pass when no limits are set
        verify(transactionLimitRepository).findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType);
        verifyNoInteractions(transactionRepository);
    }
}