package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransactionFilterRequest;
import com.suhasan.finance.transaction_service.dto.TransactionStatsResponse;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.exception.TransactionAlreadyReversedException;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {
    
    private final TransactionRepository transactionRepository;
    private final ResilientAccountServiceClient accountServiceClient;
    private final AuditService auditService;
    private final MetricsService metricsService;
    // TransactionLimitService will be added later for advanced limit checking
    
    @Override
    @Transactional
    public TransactionResponse processTransfer(TransferRequest request, String userId) {
        long startTime = System.currentTimeMillis();
        String transactionId = java.util.UUID.randomUUID().toString();
        
        log.info("Processing transfer from {} to {} for amount {}", 
                request.getFromAccountId(), request.getToAccountId(), request.getAmount());
        
        // Record transaction initiation
        auditService.logTransactionInitiated(transactionId, TransactionType.TRANSFER, 
                request.getFromAccountId(), request.getToAccountId(), request.getAmount(), userId);
        metricsService.recordTransactionInitiated(TransactionType.TRANSFER);
        
        try {
            // Validate accounts exist and are active
            AccountDto fromAccount = accountServiceClient.getAccount(request.getFromAccountId());
            AccountDto toAccount = accountServiceClient.getAccount(request.getToAccountId());
            
            if (fromAccount == null) {
                auditService.logAccountValidation(request.getFromAccountId(), userId, false, "Account not found");
                metricsService.recordTransactionFailed(TransactionType.TRANSFER, "ACCOUNT_NOT_FOUND");
                throw new IllegalArgumentException("From account not found");
            }
            
            if (toAccount == null) {
                auditService.logAccountValidation(request.getToAccountId(), userId, false, "Account not found");
                metricsService.recordTransactionFailed(TransactionType.TRANSFER, "ACCOUNT_NOT_FOUND");
                throw new IllegalArgumentException("To account not found");
            }
            
            auditService.logAccountValidation(request.getFromAccountId(), userId, true, "Account validated");
            auditService.logAccountValidation(request.getToAccountId(), userId, true, "Account validated");
        
            // Validate transaction limits
            if (!validateTransactionLimits(
                    request.getFromAccountId(), fromAccount.getAccountType(), 
                    TransactionType.TRANSFER, request.getAmount())) {
                auditService.logTransactionLimitCheck(request.getFromAccountId(), fromAccount.getAccountType(), 
                        TransactionType.TRANSFER, request.getAmount(), false, "DAILY_LIMIT", 
                        BigDecimal.valueOf(10000), userId);
                metricsService.recordTransactionFailed(TransactionType.TRANSFER, "LIMIT_EXCEEDED");
                throw new IllegalArgumentException("Transaction exceeds limits");
            }
            
            // Validate sufficient balance
            boolean hasSufficientFunds = true;
            if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
                // Check if it's a credit account with available credit
                if (!"CREDIT".equals(fromAccount.getAccountType()) || 
                    fromAccount.getAvailableCredit().compareTo(request.getAmount()) < 0) {
                    hasSufficientFunds = false;
                }
            }
            
            auditService.logBalanceCheck(request.getFromAccountId(), request.getAmount(), 
                    fromAccount.getBalance(), hasSufficientFunds, userId);
            
            if (!hasSufficientFunds) {
                metricsService.recordTransactionFailed(TransactionType.TRANSFER, "INSUFFICIENT_FUNDS");
                throw new IllegalArgumentException("Insufficient funds");
            }
        
            // Create transaction record
            Transaction transaction = Transaction.builder()
                    .transactionId(transactionId)
                    .fromAccountId(request.getFromAccountId())
                    .toAccountId(request.getToAccountId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .type(TransactionType.TRANSFER)
                    .status(TransactionStatus.PROCESSING)
                    .description(request.getDescription())
                    .reference(request.getReference())
                    .createdBy(userId)
                    .fromAccountBalanceBefore(fromAccount.getBalance())
                    .toAccountBalanceBefore(toAccount.getBalance())
                    .build();
            
            transaction = transactionRepository.save(transaction);
        
            try {
                // Update account balances via Account Service
                BigDecimal newFromBalance = fromAccount.getBalance().subtract(request.getAmount());
                BigDecimal newToBalance = toAccount.getBalance().add(request.getAmount());
                
                accountServiceClient.updateAccountBalance(request.getFromAccountId(), newFromBalance);
                accountServiceClient.updateAccountBalance(request.getToAccountId(), newToBalance);
                
                // Update transaction status and balances
                transaction.setStatus(TransactionStatus.COMPLETED);
                transaction.setProcessedBy("SYSTEM");
                transaction.setProcessedAt(LocalDateTime.now());
                transaction.setFromAccountBalanceAfter(newFromBalance);
                transaction.setToAccountBalanceAfter(newToBalance);
                
                transaction = transactionRepository.save(transaction);
                
                // Record successful completion
                long processingTime = System.currentTimeMillis() - startTime;
                auditService.logTransactionCompleted(transaction);
                metricsService.recordTransactionCompleted(TransactionType.TRANSFER, 
                        TransactionStatus.COMPLETED, request.getAmount(), processingTime);
                
                log.info("Transfer completed successfully: {}", transaction.getTransactionId());
                return mapToResponse(transaction);
                
            } catch (Exception e) {
                log.error("Transfer failed: {}", e.getMessage());
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setProcessedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
                
                // Record failure
                auditService.logTransactionFailed(transactionId, TransactionType.TRANSFER, 
                        request.getFromAccountId(), request.getToAccountId(), request.getAmount(), 
                        userId, e.getMessage(), "PROCESSING_ERROR");
                metricsService.recordTransactionFailed(TransactionType.TRANSFER, "PROCESSING_ERROR");
                
                throw new RuntimeException("Transfer failed: " + e.getMessage());
            }
            
        } catch (Exception e) {
            // Record failure for validation errors
            auditService.logTransactionFailed(transactionId, TransactionType.TRANSFER, 
                    request.getFromAccountId(), request.getToAccountId(), request.getAmount(), 
                    userId, e.getMessage(), "VALIDATION_ERROR");
            throw e;
        }
    }
    
    @Override
    @Transactional
    public TransactionResponse processDeposit(String accountId, BigDecimal amount, 
                                            String description, String userId) {
        log.info("Processing deposit to account {} for amount {}", accountId, amount);
        
        AccountDto account = accountServiceClient.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }
        
        Transaction transaction = Transaction.builder()
                .fromAccountId("EXTERNAL")
                .toAccountId(accountId)
                .amount(amount)
                .currency("USD")
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PROCESSING)
                .description(description)
                .createdBy(userId)
                .toAccountBalanceBefore(account.getBalance())
                .build();
        
        transaction = transactionRepository.save(transaction);
        
        try {
            BigDecimal newBalance = account.getBalance().add(amount);
            accountServiceClient.updateAccountBalance(accountId, newBalance);
            
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setProcessedBy("SYSTEM");
            transaction.setToAccountBalanceAfter(newBalance);
            
            transaction = transactionRepository.save(transaction);
            
            log.info("Deposit completed successfully: {}", transaction.getTransactionId());
            
        } catch (Exception e) {
            log.error("Deposit failed: {}", e.getMessage());
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            throw new RuntimeException("Deposit failed: " + e.getMessage());
        }
        
        return mapToResponse(transaction);
    }
    
    @Override
    @Transactional
    public TransactionResponse processWithdrawal(String accountId, BigDecimal amount, 
                                               String description, String userId) {
        log.info("Processing withdrawal from account {} for amount {}", accountId, amount);
        
        AccountDto account = accountServiceClient.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }
        
        // Validate transaction limits
        if (!validateTransactionLimits(
                accountId, account.getAccountType(), 
                TransactionType.WITHDRAWAL, amount)) {
            throw new IllegalArgumentException("Transaction exceeds limits");
        }
        
        // Validate sufficient balance
        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        
        Transaction transaction = Transaction.builder()
                .fromAccountId(accountId)
                .toAccountId("EXTERNAL")
                .amount(amount)
                .currency("USD")
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.PROCESSING)
                .description(description)
                .createdBy(userId)
                .fromAccountBalanceBefore(account.getBalance())
                .build();
        
        transaction = transactionRepository.save(transaction);
        
        try {
            BigDecimal newBalance = account.getBalance().subtract(amount);
            accountServiceClient.updateAccountBalance(accountId, newBalance);
            
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setProcessedBy("SYSTEM");
            transaction.setFromAccountBalanceAfter(newBalance);
            
            transaction = transactionRepository.save(transaction);
            
            log.info("Withdrawal completed successfully: {}", transaction.getTransactionId());
            
        } catch (Exception e) {
            log.error("Withdrawal failed: {}", e.getMessage());
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            throw new RuntimeException("Withdrawal failed: " + e.getMessage());
        }
        
        return mapToResponse(transaction);
    }
    
    @Override
    public TransactionResponse getTransaction(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        return mapToResponse(transaction);
    }
    
    @Override
    public Page<TransactionResponse> getAccountTransactions(String accountId, Pageable pageable) {
        Page<Transaction> transactions = transactionRepository
                .findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(accountId, accountId, pageable);
        return transactions.map(this::mapToResponse);
    }
    
    @Override
    public Page<TransactionResponse> getUserTransactions(String userId, Pageable pageable) {
        Page<Transaction> transactions = transactionRepository
                .findByCreatedByOrderByCreatedAtDesc(userId, pageable);
        return transactions.map(this::mapToResponse);
    }
    
    @Override
    @Transactional
    public TransactionResponse reverseTransaction(String transactionId, String reason, String userId) {
        log.info("Processing reversal request for transaction {} by user {}", transactionId, userId);
        
        // Requirement 6.1: Validate the original transaction exists
        Transaction originalTransaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        
        // Validate transaction can be reversed
        validateTransactionCanBeReversed(originalTransaction);
        
        // Requirement 6.4: Prevent duplicate reversals
        if (transactionRepository.isTransactionReversed(transactionId)) {
            throw new TransactionAlreadyReversedException(transactionId, 
                "Transaction " + transactionId + " has already been reversed");
        }
        
        // Requirement 6.2: Create a compensating transaction that undoes the original transaction
        Transaction reversal = createReversalTransaction(originalTransaction, reason, userId);
        reversal = transactionRepository.save(reversal);
        
        try {
            // Requirement 6.3: Update account balances to reflect the reversal
            processReversalBalanceUpdates(originalTransaction, reversal);
            
            // Complete the reversal transaction
            reversal.setStatus(TransactionStatus.COMPLETED);
            reversal.setProcessedBy("SYSTEM");
            reversal.setProcessedAt(LocalDateTime.now());
            
            // Update original transaction status and link to reversal
            originalTransaction.setStatus(TransactionStatus.REVERSED);
            originalTransaction.setReversalTransactionId(reversal.getTransactionId());
            originalTransaction.setReversedAt(LocalDateTime.now());
            originalTransaction.setReversedBy(userId);
            originalTransaction.setReversalReason(reason);
            
            // Requirement 6.5: Link reversal transaction to original transaction for audit purposes
            reversal.setOriginalTransactionId(originalTransaction.getTransactionId());
            
            // Save both transactions
            transactionRepository.save(reversal);
            transactionRepository.save(originalTransaction);
            
            // Record successful reversal
            auditService.logTransactionReversal(transactionId, reversal.getTransactionId(), reason, userId);
            auditService.logTransactionCompleted(reversal);
            metricsService.recordTransactionReversal(originalTransaction.getType());
            
            log.info("Transaction reversed successfully: {} -> {}", transactionId, reversal.getTransactionId());
            
        } catch (Exception e) {
            log.error("Transaction reversal failed for {}: {}", transactionId, e.getMessage());
            reversal.setStatus(TransactionStatus.FAILED);
            reversal.setProcessedAt(LocalDateTime.now());
            transactionRepository.save(reversal);
            
            // Record reversal failure
            auditService.logTransactionFailed(reversal.getTransactionId(), TransactionType.REVERSAL, 
                    reversal.getFromAccountId(), reversal.getToAccountId(), reversal.getAmount(), 
                    userId, e.getMessage(), "REVERSAL_ERROR");
            metricsService.recordTransactionFailed(TransactionType.REVERSAL, "REVERSAL_ERROR");
            
            throw new RuntimeException("Reversal failed: " + e.getMessage());
        }
        
        return mapToResponse(reversal);
    }
    
    /**
     * Validate that a transaction can be reversed
     */
    private void validateTransactionCanBeReversed(Transaction transaction) {
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new IllegalArgumentException("Can only reverse completed transactions. Current status: " + transaction.getStatus());
        }
        
        // Don't allow reversing reversal transactions
        if (transaction.getType() == TransactionType.REVERSAL) {
            throw new IllegalArgumentException("Cannot reverse a reversal transaction");
        }
        
        // Check if transaction is too old (business rule - can be configured)
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30); // 30 days limit
        if (transaction.getCreatedAt().isBefore(cutoffDate)) {
            throw new IllegalArgumentException("Cannot reverse transactions older than 30 days");
        }
    }
    
    /**
     * Create a compensating reversal transaction
     */
    private Transaction createReversalTransaction(Transaction originalTransaction, String reason, String userId) {
        return Transaction.builder()
                .fromAccountId(originalTransaction.getToAccountId())
                .toAccountId(originalTransaction.getFromAccountId())
                .amount(originalTransaction.getAmount())
                .currency(originalTransaction.getCurrency())
                .type(TransactionType.REVERSAL)
                .status(TransactionStatus.PROCESSING)
                .description("Reversal of transaction " + originalTransaction.getTransactionId() + ": " + reason)
                .reference("REV-" + originalTransaction.getTransactionId())
                .createdBy(userId)
                .originalTransactionId(originalTransaction.getTransactionId())
                .reversalReason(reason)
                .build();
    }
    
    /**
     * Process balance updates for reversal transaction
     */
    private void processReversalBalanceUpdates(Transaction originalTransaction, Transaction reversal) {
        // Get current account balances
        AccountDto fromAccount = accountServiceClient.getAccount(reversal.getFromAccountId());
        AccountDto toAccount = accountServiceClient.getAccount(reversal.getToAccountId());
        
        if (fromAccount == null || toAccount == null) {
            throw new IllegalArgumentException("One or both accounts not found for reversal");
        }
        
        // Record current balances before reversal
        reversal.setFromAccountBalanceBefore(fromAccount.getBalance());
        reversal.setToAccountBalanceBefore(toAccount.getBalance());
        
        // Calculate new balances (reverse the original transaction)
        BigDecimal newFromBalance = fromAccount.getBalance().subtract(reversal.getAmount());
        BigDecimal newToBalance = toAccount.getBalance().add(reversal.getAmount());
        
        // Validate that reversal won't cause negative balance (unless it's a credit account)
        if (newFromBalance.compareTo(BigDecimal.ZERO) < 0 && !"CREDIT".equals(fromAccount.getAccountType())) {
            throw new IllegalArgumentException("Reversal would cause negative balance in account " + reversal.getFromAccountId());
        }
        
        // Update account balances
        accountServiceClient.updateAccountBalance(reversal.getFromAccountId(), newFromBalance);
        accountServiceClient.updateAccountBalance(reversal.getToAccountId(), newToBalance);
        
        // Record new balances after reversal
        reversal.setFromAccountBalanceAfter(newFromBalance);
        reversal.setToAccountBalanceAfter(newToBalance);
        
        log.info("Balance updates for reversal {}: {} {} -> {}, {} {} -> {}", 
                reversal.getTransactionId(),
                reversal.getFromAccountId(), reversal.getFromAccountBalanceBefore(), newFromBalance,
                reversal.getToAccountId(), reversal.getToAccountBalanceBefore(), newToBalance);
    }
    
    @Override
    public boolean isTransactionReversed(String transactionId) {
        return transactionRepository.isTransactionReversed(transactionId);
    }
    
    @Override
    public List<TransactionResponse> getReversalTransactions(String originalTransactionId) {
        List<Transaction> reversals = transactionRepository.findReversalsByOriginalTransactionId(originalTransactionId);
        return reversals.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<TransactionResponse> getTransactionsByStatus(TransactionStatus status) {
        List<Transaction> transactions = transactionRepository.findByStatusOrderByCreatedAtDesc(status);
        return transactions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void processPendingTransactions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);
        List<Transaction> pendingTransactions = transactionRepository
                .findPendingTransactionsOlderThan(cutoffTime);
        
        for (Transaction transaction : pendingTransactions) {
            log.warn("Processing stale pending transaction: {}", transaction.getTransactionId());
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
        }
    }
    
    @Override
    public boolean validateTransactionLimits(String accountId, String accountType, 
                                           TransactionType type, BigDecimal amount) {
        // For now, implement basic validation - can be enhanced later
        if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
            log.warn("Transaction amount {} exceeds basic limit for account {}", amount, accountId);
            return false;
        }
        return true;
    }
    
    @Override
    public Page<TransactionResponse> searchTransactions(TransactionFilterRequest filter, Pageable pageable) {
        log.debug("Searching transactions with filters: {}", filter);
        
        Page<Transaction> transactions = transactionRepository.findTransactionsWithFilters(
            filter.getAccountId(),
            filter.getType(),
            filter.getStatus(),
            filter.getStartDate(),
            filter.getEndDate(),
            filter.getMinAmount(),
            filter.getMaxAmount(),
            filter.getDescription(),
            filter.getReference(),
            filter.getCreatedBy(),
            pageable
        );
        
        return transactions.map(this::mapToResponse);
    }
    
    @Override
    public TransactionStatsResponse getAccountTransactionStats(String accountId, 
                                                              LocalDateTime startDate, 
                                                              LocalDateTime endDate) {
        log.debug("Getting transaction statistics for account {} from {} to {}", accountId, startDate, endDate);
        
        // Set default date range if not provided (last 30 days)
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        // Get basic counts
        Long totalTransactions = transactionRepository.countTransactionsByAccountAndDateRange(accountId, startDate, endDate);
        Long completedTransactions = transactionRepository.countTransactionsByAccountStatusAndDateRange(accountId, TransactionStatus.COMPLETED, startDate, endDate);
        Long pendingTransactions = transactionRepository.countTransactionsByAccountStatusAndDateRange(accountId, TransactionStatus.PROCESSING, startDate, endDate);
        Long failedTransactions = transactionRepository.countTransactionsByAccountStatusAndDateRange(accountId, TransactionStatus.FAILED, startDate, endDate);
        Long reversedTransactions = transactionRepository.countTransactionsByAccountStatusAndDateRange(accountId, TransactionStatus.REVERSED, startDate, endDate);
        
        // Get amounts
        BigDecimal totalAmount = transactionRepository.sumTransactionAmountsByAccountAndDateRange(accountId, startDate, endDate);
        BigDecimal totalIncoming = transactionRepository.sumIncomingTransactionsByAccountAndDateRange(accountId, startDate, endDate);
        BigDecimal totalOutgoing = transactionRepository.sumOutgoingTransactionsByAccountAndDateRange(accountId, startDate, endDate);
        
        // Get amounts by type
        BigDecimal totalDeposits = transactionRepository.sumTransactionAmountsByAccountTypeAndDateRange(accountId, TransactionType.DEPOSIT, startDate, endDate);
        BigDecimal totalWithdrawals = transactionRepository.sumTransactionAmountsByAccountTypeAndDateRange(accountId, TransactionType.WITHDRAWAL, startDate, endDate);
        BigDecimal totalTransfers = transactionRepository.sumTransactionAmountsByAccountTypeAndDateRange(accountId, TransactionType.TRANSFER, startDate, endDate);
        
        // Get min/max amounts
        BigDecimal largestTransaction = transactionRepository.findMaxTransactionAmount(accountId, startDate, endDate);
        BigDecimal smallestTransaction = transactionRepository.findMinTransactionAmount(accountId, startDate, endDate);
        
        // Calculate average
        BigDecimal averageTransactionAmount = BigDecimal.ZERO;
        if (completedTransactions > 0 && totalAmount != null) {
            averageTransactionAmount = totalAmount.divide(BigDecimal.valueOf(completedTransactions), 2, BigDecimal.ROUND_HALF_UP);
        }
        
        // Get counts by type
        Long depositCount = transactionRepository.countTransactionsByAccountTypeAndDateRange(accountId, TransactionType.DEPOSIT, startDate, endDate);
        Long withdrawalCount = transactionRepository.countTransactionsByAccountTypeAndDateRange(accountId, TransactionType.WITHDRAWAL, startDate, endDate);
        Long transferCount = transactionRepository.countTransactionsByAccountTypeAndDateRange(accountId, TransactionType.TRANSFER, startDate, endDate);
        
        // Calculate success rate
        Double successRate = 0.0;
        if (totalTransactions > 0) {
            successRate = (completedTransactions.doubleValue() / totalTransactions.doubleValue()) * 100;
        }
        
        // Get daily/monthly totals
        BigDecimal dailyTotal = transactionRepository.getDailyTransactionSum(accountId, TransactionType.TRANSFER);
        BigDecimal monthlyTotal = transactionRepository.getMonthlyTransactionSum(accountId, TransactionType.TRANSFER);
        Long dailyCount = transactionRepository.getDailyTransactionCount(accountId, TransactionType.TRANSFER);
        Long monthlyCount = transactionRepository.getMonthlyTransactionCount(accountId, TransactionType.TRANSFER);
        
        return TransactionStatsResponse.builder()
                .accountId(accountId)
                .periodStart(startDate)
                .periodEnd(endDate)
                .totalTransactions(totalTransactions)
                .completedTransactions(completedTransactions)
                .pendingTransactions(pendingTransactions)
                .failedTransactions(failedTransactions)
                .reversedTransactions(reversedTransactions)
                .totalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO)
                .totalIncoming(totalIncoming != null ? totalIncoming : BigDecimal.ZERO)
                .totalOutgoing(totalOutgoing != null ? totalOutgoing : BigDecimal.ZERO)
                .totalDeposits(totalDeposits != null ? totalDeposits : BigDecimal.ZERO)
                .totalWithdrawals(totalWithdrawals != null ? totalWithdrawals : BigDecimal.ZERO)
                .totalTransfers(totalTransfers != null ? totalTransfers : BigDecimal.ZERO)
                .averageTransactionAmount(averageTransactionAmount)
                .largestTransaction(largestTransaction != null ? largestTransaction : BigDecimal.ZERO)
                .smallestTransaction(smallestTransaction != null ? smallestTransaction : BigDecimal.ZERO)
                .transactionCountsByType(java.util.Map.of(
                    "DEPOSIT", depositCount,
                    "WITHDRAWAL", withdrawalCount,
                    "TRANSFER", transferCount
                ))
                .transactionAmountsByType(java.util.Map.of(
                    "DEPOSIT", totalDeposits != null ? totalDeposits : BigDecimal.ZERO,
                    "WITHDRAWAL", totalWithdrawals != null ? totalWithdrawals : BigDecimal.ZERO,
                    "TRANSFER", totalTransfers != null ? totalTransfers : BigDecimal.ZERO
                ))
                .dailyTotal(dailyTotal != null ? dailyTotal : BigDecimal.ZERO)
                .monthlyTotal(monthlyTotal != null ? monthlyTotal : BigDecimal.ZERO)
                .dailyCount(dailyCount != null ? dailyCount : 0L)
                .monthlyCount(monthlyCount != null ? monthlyCount : 0L)
                .successRate(successRate)
                .currency("USD")
                .build();
    }
    
    @Override
    public TransactionStatsResponse getUserTransactionStats(String userId, 
                                                           LocalDateTime startDate, 
                                                           LocalDateTime endDate) {
        log.debug("Getting transaction statistics for user {} from {} to {}", userId, startDate, endDate);
        
        // For user stats, we'll aggregate across all their accounts
        // This is a simplified implementation - in a real system, you'd get user's accounts first
        return getAccountTransactionStats(null, startDate, endDate); // null accountId means all accounts for user
    }
    
    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .fromAccountId(transaction.getFromAccountId())
                .toAccountId(transaction.getToAccountId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .reference(transaction.getReference())
                .createdAt(transaction.getCreatedAt())
                .processedAt(transaction.getProcessedAt())
                .createdBy(transaction.getCreatedBy())
                .originalTransactionId(transaction.getOriginalTransactionId())
                .reversalTransactionId(transaction.getReversalTransactionId())
                .reversedAt(transaction.getReversedAt())
                .reversedBy(transaction.getReversedBy())
                .reversalReason(transaction.getReversalReason())
                .build();
    }
}