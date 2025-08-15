package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsService
 */
class MetricsServiceTest {
    
    private MetricsService metricsService;
    private MeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }
    
    @Test
    void testRecordTransactionInitiated() {
        assertDoesNotThrow(() -> {
            metricsService.recordTransactionInitiated(TransactionType.TRANSFER);
        });
        
        assertTrue(metricsService.getActiveTransactionsCount() > 0);
    }
    
    @Test
    void testRecordTransactionCompleted() {
        // First initiate a transaction
        metricsService.recordTransactionInitiated(TransactionType.TRANSFER);
        long initialActive = metricsService.getActiveTransactionsCount();
        
        assertDoesNotThrow(() -> {
            metricsService.recordTransactionCompleted(
                    TransactionType.TRANSFER,
                    TransactionStatus.COMPLETED,
                    BigDecimal.valueOf(100),
                    500L
            );
        });
        
        // Active count should decrease
        assertTrue(metricsService.getActiveTransactionsCount() < initialActive);
        assertTrue(metricsService.getDailyTransactionVolume() > 0);
    }
    
    @Test
    void testRecordTransactionFailed() {
        metricsService.recordTransactionInitiated(TransactionType.TRANSFER);
        long initialActive = metricsService.getActiveTransactionsCount();
        
        assertDoesNotThrow(() -> {
            metricsService.recordTransactionFailed(TransactionType.TRANSFER, "INSUFFICIENT_FUNDS");
        });
        
        // Active count should decrease
        assertTrue(metricsService.getActiveTransactionsCount() < initialActive);
    }
    
    @Test
    void testRecordAccountValidation() {
        assertDoesNotThrow(() -> {
            metricsService.recordAccountValidation(100L, true);
        });
    }
    
    @Test
    void testRecordBalanceCheck() {
        assertDoesNotThrow(() -> {
            metricsService.recordBalanceCheck(50L, true);
        });
    }
    
    @Test
    void testRecordAccountServiceError() {
        assertDoesNotThrow(() -> {
            metricsService.recordAccountServiceError("getAccount");
        });
    }
    
    @Test
    void testGetTransactionSuccessRate() {
        // Initially should be 1.0 (100%) when no transactions
        assertEquals(1.0, metricsService.getTransactionSuccessRate());
        
        // Record some transactions
        metricsService.recordTransactionInitiated(TransactionType.TRANSFER);
        metricsService.recordTransactionCompleted(
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                BigDecimal.valueOf(100),
                500L
        );
        
        // Should still be 1.0 (100%) with one successful transaction
        assertEquals(1.0, metricsService.getTransactionSuccessRate());
    }
    
    @Test
    void testResetDailyCounters() {
        // Record some transactions
        metricsService.recordTransactionInitiated(TransactionType.TRANSFER);
        metricsService.recordTransactionCompleted(
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                BigDecimal.valueOf(100),
                500L
        );
        
        assertTrue(metricsService.getDailyTransactionVolume() > 0);
        
        // Reset counters
        metricsService.resetDailyCounters();
        
        assertEquals(0, metricsService.getDailyTransactionVolume());
        assertEquals(BigDecimal.ZERO, metricsService.getDailyTransactionAmount());
    }
    
    @Test
    void testUpdatePendingTransactionsCount() {
        metricsService.updatePendingTransactionsCount(5L);
        // No direct getter for pending count, but method should not throw
        assertDoesNotThrow(() -> {
            metricsService.updatePendingTransactionsCount(10L);
        });
    }
}