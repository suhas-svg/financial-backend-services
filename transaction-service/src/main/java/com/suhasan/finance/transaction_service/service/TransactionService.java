package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransactionFilterRequest;
import com.suhasan.finance.transaction_service.dto.TransactionStatsResponse;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionService {
    
    /**
     * Process a transfer between two accounts
     */
    TransactionResponse processTransfer(TransferRequest request, String userId);
    
    /**
     * Process a deposit to an account
     */
    TransactionResponse processDeposit(String accountId, BigDecimal amount, 
                                     String description, String userId);
    
    /**
     * Process a withdrawal from an account
     */
    TransactionResponse processWithdrawal(String accountId, BigDecimal amount, 
                                        String description, String userId);
    
    /**
     * Get transaction by ID
     */
    TransactionResponse getTransaction(String transactionId);
    
    /**
     * Get transaction history for an account
     */
    Page<TransactionResponse> getAccountTransactions(String accountId, Pageable pageable);
    
    /**
     * Get transaction history for a user
     */
    Page<TransactionResponse> getUserTransactions(String userId, Pageable pageable);
    
    /**
     * Reverse a transaction
     */
    TransactionResponse reverseTransaction(String transactionId, String reason, String userId);
    
    /**
     * Check if a transaction has been reversed
     */
    boolean isTransactionReversed(String transactionId);
    
    /**
     * Get reversal transactions for an original transaction
     */
    List<TransactionResponse> getReversalTransactions(String originalTransactionId);
    
    /**
     * Get transactions by status
     */
    List<TransactionResponse> getTransactionsByStatus(TransactionStatus status);
    
    /**
     * Process pending transactions (scheduled job)
     */
    void processPendingTransactions();
    
    /**
     * Validate transaction limits
     */
    boolean validateTransactionLimits(String accountId, String accountType, 
                                    TransactionType type, BigDecimal amount);
    
    /**
     * Search transactions with filters
     */
    Page<TransactionResponse> searchTransactions(TransactionFilterRequest filter, Pageable pageable);
    
    /**
     * Get transaction statistics for an account
     */
    TransactionStatsResponse getAccountTransactionStats(String accountId, 
                                                       LocalDateTime startDate, 
                                                       LocalDateTime endDate);
    
    /**
     * Get transaction statistics for a user
     */
    TransactionStatsResponse getUserTransactionStats(String userId, 
                                                    LocalDateTime startDate, 
                                                    LocalDateTime endDate);
}