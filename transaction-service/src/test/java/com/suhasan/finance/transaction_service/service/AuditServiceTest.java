package com.suhasan.finance.transaction_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuditService
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private AuditService auditService;
    
    @Test
    void testLogTransactionInitiated() {
        // Test that the method doesn't throw exceptions
        assertDoesNotThrow(() -> {
            auditService.logTransactionInitiated(
                    "test-tx-1",
                    TransactionType.TRANSFER,
                    "acc1",
                    "acc2",
                    BigDecimal.valueOf(100),
                    "user1"
            );
        });
    }
    
    @Test
    void testLogTransactionCompleted() {
        Transaction transaction = Transaction.builder()
                .transactionId("test-tx-1")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .fromAccountId("acc1")
                .toAccountId("acc2")
                .amount(BigDecimal.valueOf(100))
                .currency("USD")
                .createdBy("user1")
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .processedAt(LocalDateTime.now())
                .build();
        
        assertDoesNotThrow(() -> {
            auditService.logTransactionCompleted(transaction);
        });
    }
    
    @Test
    void testLogTransactionFailed() {
        assertDoesNotThrow(() -> {
            auditService.logTransactionFailed(
                    "test-tx-1",
                    TransactionType.TRANSFER,
                    "acc1",
                    "acc2",
                    BigDecimal.valueOf(100),
                    "user1",
                    "Insufficient funds",
                    "INSUFFICIENT_FUNDS"
            );
        });
    }
    
    @Test
    void testLogAccountValidation() {
        assertDoesNotThrow(() -> {
            auditService.logAccountValidation("acc1", "user1", true, "Valid account");
        });
    }
    
    @Test
    void testLogBalanceCheck() {
        assertDoesNotThrow(() -> {
            auditService.logBalanceCheck(
                    "acc1",
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(500),
                    true,
                    "user1"
            );
        });
    }
    
    @Test
    void testLogSecurityEvent() {
        assertDoesNotThrow(() -> {
            auditService.logSecurityEvent(
                    "LOGIN_ATTEMPT",
                    "user1",
                    "Successful login",
                    "127.0.0.1"
            );
        });
    }
    
    @Test
    void testLogSystemEvent() {
        assertDoesNotThrow(() -> {
            auditService.logSystemEvent(
                    "STARTUP",
                    "TransactionService",
                    "Service started",
                    java.util.Map.of("version", "1.0.0")
            );
        });
    }
    
    @Test
    void testLogApiAccess() {
        assertDoesNotThrow(() -> {
            auditService.logApiAccess(
                    "/api/transactions",
                    "POST",
                    "user1",
                    "127.0.0.1",
                    200,
                    150L
            );
        });
    }
}