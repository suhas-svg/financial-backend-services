package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.RiskAlert;
import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import com.suhasan.finance.transaction_service.entity.RiskAlertType;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.RiskAlertRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class RiskEvaluationService {

    private static final BigDecimal HIGH_VALUE_TRANSFER_THRESHOLD = new BigDecimal("5000.00");
    private static final long REPEATED_FAILURE_THRESHOLD = 3;
    private static final long RAPID_TRANSFER_THRESHOLD = 5;
    private static final long REVERSAL_HEAVY_THRESHOLD = 2;

    private final RiskAlertRepository riskAlertRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public void evaluateCompletedTransaction(Transaction transaction) {
        if (transaction == null || transaction.getStatus() != TransactionStatus.COMPLETED) {
            return;
        }
        evaluateHighValueTransfer(transaction);
        evaluateRapidTransfers(transaction);
    }

    @Transactional
    public void evaluateFailedTransaction(Transaction transaction) {
        if (transaction == null || transaction.getCreatedBy() == null) {
            return;
        }
        if (transaction.getStatus() != TransactionStatus.FAILED
                && transaction.getStatus() != TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(15);
        long failures = transactionRepository.countByCreatedByAndStatusInAndCreatedAtAfter(
                transaction.getCreatedBy(),
                EnumSet.of(TransactionStatus.FAILED, TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION),
                since);
        if (failures >= REPEATED_FAILURE_THRESHOLD) {
            createAlertIfAbsent(RiskAlert.builder()
                    .alertType(RiskAlertType.REPEATED_FAILURES)
                    .severity(RiskAlertSeverity.MEDIUM)
                    .userId(transaction.getCreatedBy())
                    .transactionId(transaction.getTransactionId())
                    .fromAccountId(transaction.getFromAccountId())
                    .toAccountId(transaction.getToAccountId())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .reason(failures + " failed transactions by this user in 15 minutes")
                    .recommendation("Review recent failures for account probing, payment abuse, or account-service instability.")
                    .dedupeKey("REPEATED_FAILURES:" + transaction.getCreatedBy() + ":" + windowKey(15))
                    .metadata("{\"windowMinutes\":15,\"threshold\":3,\"observed\":" + failures + "}")
                    .build());
        }
    }

    @Transactional
    public void evaluateReversalTransaction(Transaction transaction) {
        if (transaction == null || transaction.getCreatedBy() == null
                || transaction.getType() != TransactionType.REVERSAL
                || transaction.getStatus() != TransactionStatus.COMPLETED) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long reversals = transactionRepository.countByCreatedByAndTypeAndStatusAndCreatedAtAfter(
                transaction.getCreatedBy(), TransactionType.REVERSAL, TransactionStatus.COMPLETED, since);
        if (reversals >= REVERSAL_HEAVY_THRESHOLD) {
            createAlertIfAbsent(RiskAlert.builder()
                    .alertType(RiskAlertType.REVERSAL_HEAVY_ACTIVITY)
                    .severity(RiskAlertSeverity.HIGH)
                    .userId(transaction.getCreatedBy())
                    .transactionId(transaction.getTransactionId())
                    .fromAccountId(transaction.getFromAccountId())
                    .toAccountId(transaction.getToAccountId())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .reason(reversals + " reversals by this user in 24 hours")
                    .recommendation("Inspect reversal reasons and original transactions before any operational action.")
                    .dedupeKey("REVERSAL_HEAVY_ACTIVITY:" + transaction.getCreatedBy() + ":" + windowKey(24 * 60))
                    .metadata("{\"windowHours\":24,\"threshold\":2,\"observed\":" + reversals + "}")
                    .build());
        }
    }

    private void evaluateHighValueTransfer(Transaction transaction) {
        if (transaction.getType() != TransactionType.TRANSFER
                || transaction.getAmount() == null
                || transaction.getAmount().compareTo(HIGH_VALUE_TRANSFER_THRESHOLD) < 0) {
            return;
        }
        createAlertIfAbsent(RiskAlert.builder()
                .alertType(RiskAlertType.HIGH_VALUE_TRANSFER)
                .severity(RiskAlertSeverity.HIGH)
                .userId(transaction.getCreatedBy())
                .transactionId(transaction.getTransactionId())
                .fromAccountId(transaction.getFromAccountId())
                .toAccountId(transaction.getToAccountId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .reason("Transfer amount exceeded high-value threshold of 5000.00")
                .recommendation("Review sender, recipient, and recent account activity before closing the alert.")
                .dedupeKey("HIGH_VALUE_TRANSFER:" + transaction.getTransactionId())
                .metadata("{\"threshold\":\"5000.00\"}")
                .build());
    }

    private void evaluateRapidTransfers(Transaction transaction) {
        if (transaction.getType() != TransactionType.TRANSFER || transaction.getCreatedBy() == null) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(10);
        long transfers = transactionRepository.countByCreatedByAndTypeAndStatusAndCreatedAtAfter(
                transaction.getCreatedBy(), TransactionType.TRANSFER, TransactionStatus.COMPLETED, since);
        if (transfers >= RAPID_TRANSFER_THRESHOLD) {
            createAlertIfAbsent(RiskAlert.builder()
                    .alertType(RiskAlertType.RAPID_TRANSFERS)
                    .severity(RiskAlertSeverity.MEDIUM)
                    .userId(transaction.getCreatedBy())
                    .transactionId(transaction.getTransactionId())
                    .fromAccountId(transaction.getFromAccountId())
                    .toAccountId(transaction.getToAccountId())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .reason(transfers + " completed transfers by this user in 10 minutes")
                    .recommendation("Check whether the transfer pattern matches expected customer behavior.")
                    .dedupeKey("RAPID_TRANSFERS:" + transaction.getCreatedBy() + ":" + windowKey(10))
                    .metadata("{\"windowMinutes\":10,\"threshold\":5,\"observed\":" + transfers + "}")
                    .build());
        }
    }

    private void createAlertIfAbsent(RiskAlert alert) {
        alert.setStatus(RiskAlertStatus.OPEN);
        if (!riskAlertRepository.existsByDedupeKeyAndStatus(alert.getDedupeKey(), RiskAlertStatus.OPEN)) {
            riskAlertRepository.save(alert);
        }
    }

    private String windowKey(long minutes) {
        long bucket = Math.max(1, minutes);
        long epochMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                .atZone(java.time.ZoneId.systemDefault())
                .toEpochSecond() / 60;
        return String.valueOf(epochMinute / bucket);
    }
}
