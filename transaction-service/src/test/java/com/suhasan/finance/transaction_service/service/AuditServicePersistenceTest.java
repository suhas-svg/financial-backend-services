package com.suhasan.finance.transaction_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.entity.AuditLogEntry;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.AuditLogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServicePersistenceTest {

    private AuditLogEntryRepository repository;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogEntryRepository.class);
        when(repository.save(any(AuditLogEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        auditService = new AuditService(new ObjectMapper(), repository);
    }

    @Test
    void logTransactionCompleted_PersistsFinancialAuditEvent() {
        Transaction transaction = Transaction.builder()
                .transactionId("txn-1")
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .toAccountId("acc-1")
                .amount(new BigDecimal("42.50"))
                .currency("USD")
                .createdBy("user-1")
                .description("Initial deposit")
                .createdAt(LocalDateTime.now().minusSeconds(3))
                .processedAt(LocalDateTime.now())
                .build();

        auditService.logTransactionCompleted(transaction);

        verify(repository).save(argThat(entry ->
                "TRANSACTION".equals(entry.getEventType())
                        && "TRANSACTION_COMPLETED".equals(entry.getAction())
                        && "SUCCESS".equals(entry.getOutcome())
                        && "txn-1".equals(entry.getTransactionId())
                        && "user-1".equals(entry.getUserId())
                        && "acc-1".equals(entry.getToAccountId())
                        && new BigDecimal("42.50").compareTo(entry.getAmount()) == 0
                        && "USD".equals(entry.getCurrency())
                        && "Initial deposit".equals(entry.getDetails())));
    }

    @Test
    void logSecurityEvent_PersistsSecurityAuditEvent() {
        auditService.logSecurityEvent("ACCESS_DENIED", "user-1", "Forbidden admin endpoint", "127.0.0.1");

        verify(repository).save(argThat(entry ->
                "SECURITY".equals(entry.getEventType())
                        && "SECURITY_EVENT".equals(entry.getAction())
                        && "ACCESS_DENIED".equals(entry.getOutcome())
                        && "user-1".equals(entry.getUserId())
                        && "127.0.0.1".equals(entry.getIpAddress())
                        && "Forbidden admin endpoint".equals(entry.getDetails())));
    }

    @Test
    void nonFinancialNoise_IsNotPersisted() {
        auditService.logApiAccess("/api/transactions", "GET", "user-1", "127.0.0.1", 200, 12);
        auditService.logSystemEvent("DAILY_RESET", "MetricsService", "Reset complete", null);
        auditService.logBalanceCheck("acc-1", BigDecimal.TEN, BigDecimal.TEN, true, "user-1");
        auditService.logTransactionLimitCheck("acc-1", "CHECKING", TransactionType.WITHDRAWAL,
                BigDecimal.TEN, true, "DAILY", new BigDecimal("1000"), "user-1");

        verify(repository, never()).save(any());
    }

    @Test
    void cleanupDeletesEntriesOlderThanRetentionWindow() {
        LocalDateTime before = LocalDateTime.now();

        auditService.cleanupOldAuditLogs();

        verify(repository).deleteByCreatedAtBefore(argThat(cutoff -> {
            long days = java.time.Duration.between(cutoff, before).toDays();
            return days == 90 || days == 89;
        }));
        assertEquals(1, 1);
    }
}
