package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.TransactionLimit;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.TransactionLimitRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionLimitService {
    
    private final TransactionLimitRepository transactionLimitRepository;
    private final TransactionRepository transactionRepository;
    
    /**
     * Validate if a transaction is within limits
     */
    public boolean validateTransactionLimits(String accountId, String accountType, 
                                           TransactionType transactionType, BigDecimal amount) {
        try {
            log.debug("Validating transaction limits for account {} of type {} for amount {}", 
                     accountId, accountType, amount);
            
            TransactionLimit limit = getTransactionLimit(accountType, transactionType);
            if (limit == null) {
                log.debug("No limits configured for account type {} and transaction type {}", 
                         accountType, transactionType);
                return true; // No limits configured
            }
            
            // Check per-transaction limit
            if (limit.getPerTransactionLimit() != null && 
                amount.compareTo(limit.getPerTransactionLimit()) > 0) {
                log.warn("Transaction amount {} exceeds per-transaction limit {} for account {}", 
                        amount, limit.getPerTransactionLimit(), accountId);
                return false;
            }
            
            // Check daily limits
            if (!validateDailyLimits(accountId, transactionType, amount, limit)) {
                return false;
            }
            
            // Check monthly limits
            if (!validateMonthlyLimits(accountId, transactionType, amount, limit)) {
                return false;
            }
            
            log.debug("Transaction limits validation passed for account {}", accountId);
            return true;
            
        } catch (Exception e) {
            log.error("Error validating transaction limits for account {}: {}", accountId, e.getMessage());
            return false; // Fail safe - reject transaction if validation fails
        }
    }
    
    /**
     * Get transaction limit for account type and transaction type
     */
    @Cacheable(value = "transaction:limits", key = "#accountType + '_' + #transactionType")
    public TransactionLimit getTransactionLimit(String accountType, TransactionType transactionType) {
        Optional<TransactionLimit> limit = transactionLimitRepository
                .findByAccountTypeAndTransactionTypeAndActiveTrue(accountType, transactionType);
        return limit.orElse(null);
    }
    
    /**
     * Validate daily transaction limits
     */
    private boolean validateDailyLimits(String accountId, TransactionType transactionType, 
                                       BigDecimal amount, TransactionLimit limit) {
        // Check daily amount limit
        if (limit.getDailyLimit() != null) {
            BigDecimal dailySum = transactionRepository.getDailyTransactionSum(accountId, transactionType);
            BigDecimal newDailySum = dailySum.add(amount);
            
            if (newDailySum.compareTo(limit.getDailyLimit()) > 0) {
                log.warn("Daily amount limit exceeded for account {}: current {} + new {} > limit {}", 
                        accountId, dailySum, amount, limit.getDailyLimit());
                return false;
            }
        }
        
        // Check daily count limit
        if (limit.getDailyCount() != null) {
            Long dailyCount = transactionRepository.getDailyTransactionCount(accountId, transactionType);
            
            if (dailyCount >= limit.getDailyCount()) {
                log.warn("Daily count limit exceeded for account {}: current {} >= limit {}", 
                        accountId, dailyCount, limit.getDailyCount());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate monthly transaction limits
     */
    private boolean validateMonthlyLimits(String accountId, TransactionType transactionType, 
                                        BigDecimal amount, TransactionLimit limit) {
        // Check monthly amount limit
        if (limit.getMonthlyLimit() != null) {
            BigDecimal monthlySum = transactionRepository.getMonthlyTransactionSum(accountId, transactionType);
            BigDecimal newMonthlySum = monthlySum.add(amount);
            
            if (newMonthlySum.compareTo(limit.getMonthlyLimit()) > 0) {
                log.warn("Monthly amount limit exceeded for account {}: current {} + new {} > limit {}", 
                        accountId, monthlySum, amount, limit.getMonthlyLimit());
                return false;
            }
        }
        
        // Check monthly count limit
        if (limit.getMonthlyCount() != null) {
            Long monthlyCount = transactionRepository.getMonthlyTransactionCount(accountId, transactionType);
            
            if (monthlyCount >= limit.getMonthlyCount()) {
                log.warn("Monthly count limit exceeded for account {}: current {} >= limit {}", 
                        accountId, monthlyCount, limit.getMonthlyCount());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Create or update transaction limit
     */
    public TransactionLimit saveTransactionLimit(TransactionLimit limit) {
        log.info("Saving transaction limit for account type {} and transaction type {}", 
                limit.getAccountType(), limit.getTransactionType());
        return transactionLimitRepository.save(limit);
    }
    
    /**
     * Get remaining daily limit for an account
     */
    public BigDecimal getRemainingDailyLimit(String accountId, String accountType, TransactionType transactionType) {
        TransactionLimit limit = getTransactionLimit(accountType, transactionType);
        if (limit == null || limit.getDailyLimit() == null) {
            return null; // No limit configured
        }
        
        BigDecimal dailySum = transactionRepository.getDailyTransactionSum(accountId, transactionType);
        BigDecimal remaining = limit.getDailyLimit().subtract(dailySum);
        
        return remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
    }
    
    /**
     * Get remaining monthly limit for an account
     */
    public BigDecimal getRemainingMonthlyLimit(String accountId, String accountType, TransactionType transactionType) {
        TransactionLimit limit = getTransactionLimit(accountType, transactionType);
        if (limit == null || limit.getMonthlyLimit() == null) {
            return null; // No limit configured
        }
        
        BigDecimal monthlySum = transactionRepository.getMonthlyTransactionSum(accountId, transactionType);
        BigDecimal remaining = limit.getMonthlyLimit().subtract(monthlySum);
        
        return remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
    }
}
