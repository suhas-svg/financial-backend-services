package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for handling custom metrics collection
 */
@Service
@Slf4j
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    
    // Counters for transaction operations
    private final Counter transactionInitiatedCounter;
    private final Counter transactionCompletedCounter;
    private final Counter transactionFailedCounter;
    private final Counter transactionReversedCounter;
    
    // Counters by transaction type
    private final ConcurrentHashMap<TransactionType, Counter> transactionTypeCounters = new ConcurrentHashMap<>();
    
    // Counters by transaction status
    private final ConcurrentHashMap<TransactionStatus, Counter> transactionStatusCounters = new ConcurrentHashMap<>();
    
    // Timers for performance metrics
    private final Timer transactionProcessingTimer;
    private final Timer accountValidationTimer;
    private final Timer balanceCheckTimer;
    
    // Gauges for current state
    private final AtomicLong activeTransactionsCount = new AtomicLong(0);
    private final AtomicLong pendingTransactionsCount = new AtomicLong(0);
    private final AtomicLong dailyTransactionVolume = new AtomicLong(0);
    private final AtomicLong dailyTransactionAmount = new AtomicLong(0);
    
    // Error counters
    private final Counter insufficientFundsCounter;
    private final Counter accountNotFoundCounter;
    private final Counter limitExceededCounter;
    private final Counter accountServiceErrorCounter;
    
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize main counters
        this.transactionInitiatedCounter = Counter.builder("transaction.initiated.total")
                .description("Total number of transactions initiated")
                .register(meterRegistry);
                
        this.transactionCompletedCounter = Counter.builder("transaction.completed.total")
                .description("Total number of transactions completed successfully")
                .register(meterRegistry);
                
        this.transactionFailedCounter = Counter.builder("transaction.failed.total")
                .description("Total number of transactions that failed")
                .register(meterRegistry);
                
        this.transactionReversedCounter = Counter.builder("transaction.reversed.total")
                .description("Total number of transactions reversed")
                .register(meterRegistry);
        
        // Initialize timers
        this.transactionProcessingTimer = Timer.builder("transaction.processing.duration")
                .description("Time taken to process transactions")
                .register(meterRegistry);
                
        this.accountValidationTimer = Timer.builder("account.validation.duration")
                .description("Time taken to validate accounts")
                .register(meterRegistry);
                
        this.balanceCheckTimer = Timer.builder("balance.check.duration")
                .description("Time taken to check account balance")
                .register(meterRegistry);
        
        // Initialize error counters
        this.insufficientFundsCounter = Counter.builder("transaction.error.insufficient_funds.total")
                .description("Total number of insufficient funds errors")
                .register(meterRegistry);
                
        this.accountNotFoundCounter = Counter.builder("transaction.error.account_not_found.total")
                .description("Total number of account not found errors")
                .register(meterRegistry);
                
        this.limitExceededCounter = Counter.builder("transaction.error.limit_exceeded.total")
                .description("Total number of transaction limit exceeded errors")
                .register(meterRegistry);
                
        this.accountServiceErrorCounter = Counter.builder("account.service.error.total")
                .description("Total number of Account Service communication errors")
                .register(meterRegistry);
        
        // Initialize gauges
        Gauge.builder("transaction.active.count", activeTransactionsCount, AtomicLong::doubleValue)
                .description("Current number of active transactions")
                .register(meterRegistry);
                
        Gauge.builder("transaction.pending.count", pendingTransactionsCount, AtomicLong::doubleValue)
                .description("Current number of pending transactions")
                .register(meterRegistry);
                
        Gauge.builder("transaction.daily.volume", dailyTransactionVolume, AtomicLong::doubleValue)
                .description("Daily transaction volume count")
                .register(meterRegistry);
                
        Gauge.builder("transaction.daily.amount", dailyTransactionAmount, AtomicLong::doubleValue)
                .description("Daily transaction amount in cents")
                .register(meterRegistry);
        
        // Initialize transaction type counters
        for (TransactionType type : TransactionType.values()) {
            transactionTypeCounters.put(type, 
                Counter.builder("transaction.type.total")
                        .description("Total transactions by type")
                        .tag("type", type.name())
                        .register(meterRegistry));
        }
        
        // Initialize transaction status counters
        for (TransactionStatus status : TransactionStatus.values()) {
            transactionStatusCounters.put(status,
                Counter.builder("transaction.status.total")
                        .description("Total transactions by status")
                        .tag("status", status.name())
                        .register(meterRegistry));
        }
    }
    
    /**
     * Record transaction initiation
     */
    public void recordTransactionInitiated(TransactionType type) {
        transactionInitiatedCounter.increment();
        transactionTypeCounters.get(type).increment();
        activeTransactionsCount.incrementAndGet();
        log.debug("Recorded transaction initiated: type={}", type);
    }
    
    /**
     * Record transaction completion
     */
    public void recordTransactionCompleted(TransactionType type, TransactionStatus status, 
                                         BigDecimal amount, long processingTimeMs) {
        transactionCompletedCounter.increment();
        transactionTypeCounters.get(type).increment();
        transactionStatusCounters.get(status).increment();
        transactionProcessingTimer.record(processingTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        activeTransactionsCount.decrementAndGet();
        dailyTransactionVolume.incrementAndGet();
        dailyTransactionAmount.addAndGet(amount.multiply(BigDecimal.valueOf(100)).longValue()); // Convert to cents
        
        log.debug("Recorded transaction completed: type={}, status={}, amount={}, time={}ms", 
                type, status, amount, processingTimeMs);
    }
    
    /**
     * Record transaction failure
     */
    public void recordTransactionFailed(TransactionType type, String errorType) {
        transactionFailedCounter.increment();
        transactionTypeCounters.get(type).increment();
        transactionStatusCounters.get(TransactionStatus.FAILED).increment();
        
        activeTransactionsCount.decrementAndGet();
        
        // Record specific error types
        switch (errorType.toUpperCase()) {
            case "INSUFFICIENT_FUNDS" -> insufficientFundsCounter.increment();
            case "ACCOUNT_NOT_FOUND" -> accountNotFoundCounter.increment();
            case "LIMIT_EXCEEDED" -> limitExceededCounter.increment();
            case "ACCOUNT_SERVICE_ERROR" -> accountServiceErrorCounter.increment();
        }
        
        log.debug("Recorded transaction failed: type={}, errorType={}", type, errorType);
    }
    
    /**
     * Record transaction reversal
     */
    public void recordTransactionReversal(TransactionType originalType) {
        transactionReversedCounter.increment();
        transactionTypeCounters.get(TransactionType.REVERSAL).increment();
        transactionStatusCounters.get(TransactionStatus.COMPLETED).increment();
        
        log.debug("Recorded transaction reversal: originalType={}", originalType);
    }
    
    /**
     * Record account validation time
     */
    public void recordAccountValidation(long validationTimeMs, boolean successful) {
        accountValidationTimer.record(validationTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        if (!successful) {
            accountNotFoundCounter.increment();
        }
        
        log.debug("Recorded account validation: time={}ms, successful={}", validationTimeMs, successful);
    }
    
    /**
     * Record balance check time
     */
    public void recordBalanceCheck(long checkTimeMs, boolean sufficient) {
        balanceCheckTimer.record(checkTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        if (!sufficient) {
            insufficientFundsCounter.increment();
        }
        
        log.debug("Recorded balance check: time={}ms, sufficient={}", checkTimeMs, sufficient);
    }
    
    /**
     * Record Account Service error
     */
    public void recordAccountServiceError(String operation) {
        accountServiceErrorCounter.increment();
        
        // Create specific counter for the operation if it doesn't exist
        Counter operationErrorCounter = Counter.builder("account.service.operation.error.total")
                .description("Account Service errors by operation")
                .tag("operation", operation)
                .register(meterRegistry);
        operationErrorCounter.increment();
        
        log.debug("Recorded Account Service error: operation={}", operation);
    }
    
    /**
     * Update pending transactions count
     */
    public void updatePendingTransactionsCount(long count) {
        pendingTransactionsCount.set(count);
        log.debug("Updated pending transactions count: {}", count);
    }
    
    /**
     * Reset daily counters (called by scheduled job)
     */
    public void resetDailyCounters() {
        dailyTransactionVolume.set(0);
        dailyTransactionAmount.set(0);
        log.info("Reset daily transaction counters");
    }
    
    /**
     * Get current transaction success rate
     */
    public double getTransactionSuccessRate() {
        double completed = transactionCompletedCounter.count();
        double failed = transactionFailedCounter.count();
        double total = completed + failed;
        
        if (total == 0) {
            return 1.0; // 100% if no transactions yet
        }
        
        return completed / total;
    }
    
    /**
     * Get current daily transaction volume
     */
    public long getDailyTransactionVolume() {
        return dailyTransactionVolume.get();
    }
    
    /**
     * Get current daily transaction amount
     */
    public BigDecimal getDailyTransactionAmount() {
        return BigDecimal.valueOf(dailyTransactionAmount.get()).divide(BigDecimal.valueOf(100)); // Convert from cents
    }
    
    /**
     * Get active transactions count
     */
    public long getActiveTransactionsCount() {
        return activeTransactionsCount.get();
    }
}