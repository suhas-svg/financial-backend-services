package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransactionFilterRequest;
import com.suhasan.finance.transaction_service.dto.TransactionStatsResponse;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionProcessingState;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.exception.AccountServiceUnavailableException;
import com.suhasan.finance.transaction_service.exception.TransactionAlreadyReversedException;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class TransactionServiceImpl implements TransactionService {
    
    private final TransactionRepository transactionRepository;
    private final ResilientAccountServiceClient accountServiceClient;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final TransactionLimitService transactionLimitService;
    
    @Override
    @Transactional(noRollbackFor = Exception.class)
    @CacheEvict(value = "transaction:history", allEntries = true)
    public TransactionResponse processTransfer(TransferRequest request, String userId, String idempotencyKey) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<Transaction> existing = findIdempotentTransaction(userId, TransactionType.TRANSFER, normalizedIdempotencyKey);
        if (existing.isPresent()) {
            return mapToResponse(existing.get());
        }

        long startTime = System.currentTimeMillis();
        String transactionId = UUID.randomUUID().toString();
        AccountDto fromAccount = accountServiceClient.getAccount(request.getFromAccountId());
        if (fromAccount == null) {
            throw new IllegalArgumentException("From account not found");
        }
        ensureAccountOwnedByUser(fromAccount, userId);

        if (!validateTransactionLimits(
                request.getFromAccountId(), fromAccount.getAccountType(),
                TransactionType.TRANSFER, request.getAmount())) {
            throw new IllegalArgumentException("Transaction exceeds limits");
        }

        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        Transaction transaction = Transaction.builder()
                .transactionId(transactionId)
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PROCESSING)
                .processingState(TransactionProcessingState.INITIATED)
                .description(request.getDescription())
                .reference(request.getReference())
                .idempotencyKey(normalizedIdempotencyKey)
                .createdBy(userId)
                .fromAccountBalanceBefore(fromAccount.getBalance())
                .build();
        transaction = transactionRepository.save(transaction);

        auditService.logTransactionInitiated(transactionId, TransactionType.TRANSFER,
                request.getFromAccountId(), request.getToAccountId(), request.getAmount(), userId);
        metricsService.recordTransactionInitiated(TransactionType.TRANSFER);

        boolean debitApplied = false;
        try {
            ResilientAccountServiceClient.BalanceOperationResponse debitResult = accountServiceClient.applyBalanceOperation(
                    request.getFromAccountId(),
                    transactionId + ":debit",
                    request.getAmount().negate(),
                    transactionId,
                    "TRANSFER_DEBIT",
                    false
            );
            debitApplied = debitResult.isApplied();
            transaction.setFromAccountBalanceAfter(debitResult.getNewBalance());
            transaction.setProcessingState(TransactionProcessingState.DEBIT_APPLIED);
            transactionRepository.save(transaction);
            if (!debitResult.isApplied()) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setProcessedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
                throw new IllegalArgumentException("Insufficient funds");
            }

            ResilientAccountServiceClient.BalanceOperationResponse creditResult = accountServiceClient.applyBalanceOperation(
                    request.getToAccountId(),
                    transactionId + ":credit",
                    request.getAmount(),
                    transactionId,
                    "TRANSFER_CREDIT",
                    true
            );
            transaction.setToAccountBalanceAfter(creditResult.getNewBalance());
            transaction.setProcessingState(TransactionProcessingState.CREDIT_APPLIED);
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setProcessedBy("SYSTEM");
            transaction.setProcessedAt(LocalDateTime.now());
            transaction.setProcessingState(TransactionProcessingState.COMPLETED);
            transaction = transactionRepository.save(transaction);

            long processingTime = System.currentTimeMillis() - startTime;
            auditService.logTransactionCompleted(transaction);
            metricsService.recordTransactionCompleted(TransactionType.TRANSFER,
                    TransactionStatus.COMPLETED, request.getAmount(), processingTime);
            return mapToResponse(transaction);
        } catch (Exception e) {
            if (debitApplied) {
                try {
                    accountServiceClient.applyBalanceOperation(
                            request.getFromAccountId(),
                            transactionId + ":compensate",
                            request.getAmount(),
                            transactionId,
                            "TRANSFER_COMPENSATION",
                            true
                    );
                    transaction.setProcessingState(TransactionProcessingState.COMPENSATED);
                    transaction.setStatus(TransactionStatus.FAILED);
                } catch (Exception compensationError) {
                    transaction.setProcessingState(TransactionProcessingState.MANUAL_ACTION_REQUIRED);
                    transaction.setStatus(TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION);
                }
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
            }
            transaction.setProcessedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            auditService.logTransactionFailed(transactionId, TransactionType.TRANSFER,
                    request.getFromAccountId(), request.getToAccountId(), request.getAmount(),
                    userId, e.getMessage(), "PROCESSING_ERROR");
            metricsService.recordTransactionFailed(TransactionType.TRANSFER, "PROCESSING_ERROR");
            throw new RuntimeException("Transfer failed: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional(noRollbackFor = Exception.class)
    @CacheEvict(value = "transaction:history", allEntries = true)
    public TransactionResponse processDeposit(String accountId, BigDecimal amount,
                                              String description, String userId, String idempotencyKey) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<Transaction> existing = findIdempotentTransaction(userId, TransactionType.DEPOSIT, normalizedIdempotencyKey);
        if (existing.isPresent()) {
            return mapToResponse(existing.get());
        }

        AccountDto account = accountServiceClient.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }
        ensureAccountOwnedByUser(account, userId);

        if (!validateTransactionLimits(accountId, account.getAccountType(), TransactionType.DEPOSIT, amount)) {
            throw new IllegalArgumentException("Transaction exceeds limits");
        }

        Transaction transaction = Transaction.builder()
                .fromAccountId("EXTERNAL")
                .toAccountId(accountId)
                .amount(amount)
                .currency("USD")
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PROCESSING)
                .processingState(TransactionProcessingState.INITIATED)
                .description(description)
                .idempotencyKey(normalizedIdempotencyKey)
                .createdBy(userId)
                .toAccountBalanceBefore(account.getBalance())
                .build();
        transaction = transactionRepository.save(transaction);

        try {
            ResilientAccountServiceClient.BalanceOperationResponse response = accountServiceClient.applyBalanceOperation(
                    accountId,
                    transaction.getTransactionId() + ":deposit",
                    amount,
                    transaction.getTransactionId(),
                    "DEPOSIT",
                    true
            );
            transaction.setToAccountBalanceAfter(response.getNewBalance());
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setProcessedBy("SYSTEM");
            transaction.setProcessedAt(LocalDateTime.now());
            transaction.setProcessingState(TransactionProcessingState.COMPLETED);
            transaction = transactionRepository.save(transaction);
            return mapToResponse(transaction);
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setProcessedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            throw new RuntimeException("Deposit failed: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional(noRollbackFor = Exception.class)
    @CacheEvict(value = "transaction:history", allEntries = true)
    public TransactionResponse processWithdrawal(String accountId, BigDecimal amount,
                                                 String description, String userId, String idempotencyKey) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<Transaction> existing = findIdempotentTransaction(userId, TransactionType.WITHDRAWAL, normalizedIdempotencyKey);
        if (existing.isPresent()) {
            return mapToResponse(existing.get());
        }

        AccountDto account = accountServiceClient.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }
        ensureAccountOwnedByUser(account, userId);

        if (!validateTransactionLimits(accountId, account.getAccountType(), TransactionType.WITHDRAWAL, amount)) {
            throw new IllegalArgumentException("Transaction exceeds limits");
        }
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
                .processingState(TransactionProcessingState.INITIATED)
                .description(description)
                .idempotencyKey(normalizedIdempotencyKey)
                .createdBy(userId)
                .fromAccountBalanceBefore(account.getBalance())
                .build();
        transaction = transactionRepository.save(transaction);

        try {
            ResilientAccountServiceClient.BalanceOperationResponse response = accountServiceClient.applyBalanceOperation(
                    accountId,
                    transaction.getTransactionId() + ":withdrawal",
                    amount.negate(),
                    transaction.getTransactionId(),
                    "WITHDRAWAL",
                    false
            );
            if (!response.isApplied()) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setProcessedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
                throw new IllegalArgumentException("Insufficient funds");
            }
            transaction.setFromAccountBalanceAfter(response.getNewBalance());
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setProcessedBy("SYSTEM");
            transaction.setProcessedAt(LocalDateTime.now());
            transaction.setProcessingState(TransactionProcessingState.COMPLETED);
            transaction = transactionRepository.save(transaction);
            return mapToResponse(transaction);
        } catch (Exception e) {
            if (transaction.getStatus() != TransactionStatus.COMPLETED) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setProcessedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
            }
            throw new RuntimeException("Withdrawal failed: " + e.getMessage());
        }
    }
    
    @Override
    public TransactionResponse getTransaction(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        assertCanAccessTransaction(transaction);
        return mapToResponse(transaction);
    }
    
    @Override
    @Cacheable(value = "transaction:history",
            key = "#accountId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()")
    public Page<TransactionResponse> getAccountTransactions(String accountId, Pageable pageable) {
        assertCanAccessAccountScope(accountId);
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
    @Transactional(noRollbackFor = Exception.class)
    @CacheEvict(value = "transaction:history", allEntries = true)
    public TransactionResponse reverseTransaction(String transactionId, String reason, String userId, String idempotencyKey) {
        log.info("Processing reversal request for transaction {} by user {}", transactionId, userId);

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<Transaction> existing = findIdempotentTransaction(userId, TransactionType.REVERSAL, normalizedIdempotencyKey);
        if (existing.isPresent()) {
            return mapToResponse(existing.get());
        }
        
        // Requirement 6.1: Validate the original transaction exists
        Transaction originalTransaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        assertCanAccessTransaction(originalTransaction);
        
        // Requirement 6.4: Prevent duplicate reversals
        if (originalTransaction.getStatus() == TransactionStatus.REVERSED
                || transactionRepository.isTransactionReversed(transactionId)) {
            throw new TransactionAlreadyReversedException(transactionId, 
                "Transaction " + transactionId + " has already been reversed");
        }

        // Validate transaction can be reversed
        validateTransactionCanBeReversed(originalTransaction);
        
        // Requirement 6.2: Create a compensating transaction that undoes the original transaction
        Transaction reversal = createReversalTransaction(originalTransaction, reason, userId);
        reversal.setIdempotencyKey(normalizedIdempotencyKey);
        reversal = transactionRepository.save(reversal);
        
        try {
            // Requirement 6.3: Update account balances to reflect the reversal
            processReversalBalanceUpdates(originalTransaction, reversal);
            
            // Complete the reversal transaction
            reversal.setStatus(TransactionStatus.COMPLETED);
            reversal.setProcessingState(TransactionProcessingState.COMPLETED);
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
            
        } catch (AccountServiceUnavailableException e) {
            log.error("Transaction reversal failed due to account service unavailability for {}: {}", transactionId, e.getMessage());
            reversal.setStatus(TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION);
            reversal.setProcessingState(TransactionProcessingState.MANUAL_ACTION_REQUIRED);
            reversal.setProcessedAt(LocalDateTime.now());
            transactionRepository.save(reversal);
            throw e;
        } catch (Exception e) {
            log.error("Transaction reversal failed for {}: {}", transactionId, e.getMessage());
            if (reversal.getStatus() != TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION) {
                reversal.setStatus(TransactionStatus.FAILED);
            }
            if (reversal.getProcessingState() == null) {
                reversal.setProcessingState(TransactionProcessingState.MANUAL_ACTION_REQUIRED);
            }
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
                .processingState(TransactionProcessingState.INITIATED)
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
        boolean debitApplied = false;
        try {
            if (!isExternalAccount(reversal.getFromAccountId())) {
                ResilientAccountServiceClient.BalanceOperationResponse debit = accountServiceClient.applyBalanceOperation(
                        reversal.getFromAccountId(),
                        reversal.getTransactionId() + ":debit",
                        reversal.getAmount().negate(),
                        reversal.getTransactionId(),
                        "REVERSAL_DEBIT",
                        false
                );
                debitApplied = debit.isApplied();
                reversal.setFromAccountBalanceAfter(debit.getNewBalance());
                if (!debit.isApplied()) {
                    throw new IllegalArgumentException("Insufficient funds for reversal debit");
                }
            }

            if (!isExternalAccount(reversal.getToAccountId())) {
                ResilientAccountServiceClient.BalanceOperationResponse credit = accountServiceClient.applyBalanceOperation(
                        reversal.getToAccountId(),
                        reversal.getTransactionId() + ":credit",
                        reversal.getAmount(),
                        reversal.getTransactionId(),
                        "REVERSAL_CREDIT",
                        true
                );
                reversal.setToAccountBalanceAfter(credit.getNewBalance());
            }
            reversal.setProcessingState(TransactionProcessingState.COMPLETED);
        } catch (Exception e) {
            if (debitApplied && !isExternalAccount(reversal.getFromAccountId())) {
                try {
                    accountServiceClient.applyBalanceOperation(
                            reversal.getFromAccountId(),
                            reversal.getTransactionId() + ":compensate",
                            reversal.getAmount(),
                            reversal.getTransactionId(),
                            "REVERSAL_COMPENSATION",
                            true
                    );
                    reversal.setProcessingState(TransactionProcessingState.COMPENSATED);
                } catch (Exception compensationError) {
                    reversal.setStatus(TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION);
                    reversal.setProcessingState(TransactionProcessingState.MANUAL_ACTION_REQUIRED);
                }
            }
            throw e;
        }
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
        try {
            if (!transactionLimitService.validateTransactionLimits(accountId, accountType, type, amount)) {
                return false;
            }
        } catch (Exception ex) {
            log.warn("Advanced limit validation unavailable, falling back to basic limits: {}", ex.getMessage());
        }

        if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
            log.warn("Transaction amount {} exceeds basic limit for account {}", amount, accountId);
            return false;
        }
        return true;
    }
    
    @Override
    public Page<TransactionResponse> searchTransactions(TransactionFilterRequest filter, Pageable pageable) {
        log.debug("Searching transactions with filters: {}", filter);

        List<Transaction> scopedTransactions;
        if (filter.getCreatedBy() != null && !filter.getCreatedBy().isBlank()) {
            scopedTransactions = transactionRepository
                    .findByCreatedByOrderByCreatedAtDesc(filter.getCreatedBy(), Pageable.unpaged())
                    .getContent();
        } else {
            scopedTransactions = transactionRepository.findAll();
        }

        List<Transaction> filteredTransactions = scopedTransactions.stream()
                .filter(transaction -> matchesTransactionFilter(transaction, filter))
                .collect(Collectors.toList());

        filteredTransactions.sort(buildTransactionSortComparator(pageable.getSort()));

        int fromIndex = Math.toIntExact(pageable.getOffset());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), filteredTransactions.size());
        List<Transaction> pageContent = fromIndex >= filteredTransactions.size()
                ? List.of()
                : filteredTransactions.subList(fromIndex, toIndex);

        Page<Transaction> page = new PageImpl<>(pageContent, pageable, filteredTransactions.size());
        return page.map(this::mapToResponse);
    }
    
    @Override
    public TransactionStatsResponse getAccountTransactionStats(String accountId, 
                                                              LocalDateTime startDate, 
                                                              LocalDateTime endDate) {
        log.debug("Getting transaction statistics for account {} from {} to {}", accountId, startDate, endDate);

        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID is required for account statistics");
        }
        assertCanAccessAccountScope(accountId);

        List<Transaction> accountTransactions = transactionRepository
                .findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(accountId, accountId, Pageable.unpaged())
                .getContent();

        return buildTransactionStats(accountId, accountTransactions, startDate, endDate);
    }
    
    @Override
    public TransactionStatsResponse getUserTransactionStats(String userId, 
                                                           LocalDateTime startDate, 
                                                           LocalDateTime endDate) {
        log.debug("Getting transaction statistics for user {} from {} to {}", userId, startDate, endDate);

        List<Transaction> userTransactions = transactionRepository
                .findByCreatedByOrderByCreatedAtDesc(userId, Pageable.unpaged())
                .getContent();

        return buildTransactionStats(null, userTransactions, startDate, endDate);
    }

    private TransactionStatsResponse buildTransactionStats(String accountId,
                                                           List<Transaction> scopedTransactions,
                                                           LocalDateTime startDate,
                                                           LocalDateTime endDate) {
        LocalDateTime resolvedStart = startDate != null ? startDate : LocalDateTime.now().minusDays(30);
        LocalDateTime resolvedEnd = endDate != null ? endDate : LocalDateTime.now();

        List<Transaction> periodTransactions = scopedTransactions.stream()
                .filter(t -> t.getCreatedAt() != null)
                .filter(t -> !t.getCreatedAt().isBefore(resolvedStart) && !t.getCreatedAt().isAfter(resolvedEnd))
                .collect(Collectors.toList());

        long totalTransactions = periodTransactions.size();
        long completedTransactions = periodTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .count();
        long pendingTransactions = periodTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.PROCESSING)
                .count();
        long failedTransactions = periodTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.FAILED)
                .count();
        long reversedTransactions = periodTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.REVERSED)
                .count();

        List<Transaction> completedInPeriod = periodTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .collect(Collectors.toList());

        BigDecimal totalAmount = completedInPeriod.stream()
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIncoming;
        BigDecimal totalOutgoing;
        if (accountId != null) {
            totalIncoming = completedInPeriod.stream()
                    .filter(t -> accountId.equals(t.getToAccountId()))
                    .map(Transaction::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalOutgoing = completedInPeriod.stream()
                    .filter(t -> accountId.equals(t.getFromAccountId()))
                    .map(Transaction::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            totalIncoming = completedInPeriod.stream()
                    .filter(t -> isExternalAccount(t.getFromAccountId()))
                    .map(Transaction::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalOutgoing = completedInPeriod.stream()
                    .filter(t -> isExternalAccount(t.getToAccountId()))
                    .map(Transaction::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal totalDeposits = sumByType(completedInPeriod, TransactionType.DEPOSIT);
        BigDecimal totalWithdrawals = sumByType(completedInPeriod, TransactionType.WITHDRAWAL);
        BigDecimal totalTransfers = sumByType(completedInPeriod, TransactionType.TRANSFER);

        long depositCount = countByType(completedInPeriod, TransactionType.DEPOSIT);
        long withdrawalCount = countByType(completedInPeriod, TransactionType.WITHDRAWAL);
        long transferCount = countByType(completedInPeriod, TransactionType.TRANSFER);

        BigDecimal largestTransaction = completedInPeriod.stream()
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal smallestTransaction = completedInPeriod.stream()
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal averageTransactionAmount = BigDecimal.ZERO;
        if (completedTransactions > 0) {
            averageTransactionAmount = totalAmount.divide(
                    BigDecimal.valueOf(completedTransactions),
                    2,
                    RoundingMode.HALF_UP
            );
        }

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();
        List<Transaction> completedTransfers = scopedTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .filter(t -> t.getType() == TransactionType.TRANSFER)
                .filter(t -> t.getCreatedAt() != null)
                .collect(Collectors.toList());

        BigDecimal dailyTotal = completedTransfers.stream()
                .filter(t -> t.getCreatedAt().toLocalDate().isEqual(today))
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlyTotal = completedTransfers.stream()
                .filter(t -> YearMonth.from(t.getCreatedAt()).equals(currentMonth))
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long dailyCount = completedTransfers.stream()
                .filter(t -> t.getCreatedAt().toLocalDate().isEqual(today))
                .count();
        long monthlyCount = completedTransfers.stream()
                .filter(t -> YearMonth.from(t.getCreatedAt()).equals(currentMonth))
                .count();

        double successRate = totalTransactions > 0
                ? (completedTransactions * 100.0) / totalTransactions
                : 0.0;

        return TransactionStatsResponse.builder()
                .accountId(accountId)
                .periodStart(resolvedStart)
                .periodEnd(resolvedEnd)
                .totalTransactions(totalTransactions)
                .completedTransactions(completedTransactions)
                .pendingTransactions(pendingTransactions)
                .failedTransactions(failedTransactions)
                .reversedTransactions(reversedTransactions)
                .totalAmount(totalAmount)
                .totalIncoming(totalIncoming)
                .totalOutgoing(totalOutgoing)
                .totalDeposits(totalDeposits)
                .totalWithdrawals(totalWithdrawals)
                .totalTransfers(totalTransfers)
                .averageTransactionAmount(averageTransactionAmount)
                .largestTransaction(largestTransaction)
                .smallestTransaction(smallestTransaction)
                .transactionCountsByType(java.util.Map.of(
                        "DEPOSIT", depositCount,
                        "WITHDRAWAL", withdrawalCount,
                        "TRANSFER", transferCount
                ))
                .transactionAmountsByType(java.util.Map.of(
                        "DEPOSIT", totalDeposits,
                        "WITHDRAWAL", totalWithdrawals,
                        "TRANSFER", totalTransfers
                ))
                .dailyTotal(dailyTotal)
                .monthlyTotal(monthlyTotal)
                .dailyCount(dailyCount)
                .monthlyCount(monthlyCount)
                .successRate(successRate)
                .currency("USD")
                .build();
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Optional<Transaction> findIdempotentTransaction(String userId,
                                                            TransactionType type,
                                                            String normalizedIdempotencyKey) {
        if (normalizedIdempotencyKey == null) {
            return Optional.empty();
        }
        return transactionRepository.findFirstByCreatedByAndTypeAndIdempotencyKey(
                userId, type, normalizedIdempotencyKey);
    }

    private void ensureAccountOwnedByUser(AccountDto account, String userId) {
        if (isPrivilegedRequester()) {
            return;
        }
        if (account.getOwnerId() == null || account.getOwnerId().isBlank()) {
            return;
        }
        if (userId == null || userId.isBlank()) {
            return;
        }
        if (account.getOwnerId() == null || !account.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("User is not authorized for account " + account.getId());
        }
    }

    private void assertCanAccessAccountScope(String accountId) {
        if (isPrivilegedRequester()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        AccountDto account = accountServiceClient.getAccount(accountId);
        if (account == null) {
            throw new AccessDeniedException("Account not found or not accessible");
        }
        if (account.getOwnerId() == null || !account.getOwnerId().equals(authentication.getName())) {
            throw new AccessDeniedException("Not authorized for account " + accountId);
        }
    }

    private void assertCanAccessTransaction(Transaction transaction) {
        if (isPrivilegedRequester()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        if (!authentication.getName().equals(transaction.getCreatedBy())) {
            throw new AccessDeniedException("Not authorized to view this transaction");
        }
    }

    private boolean isPrivilegedRequester() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_INTERNAL_SERVICE"));
    }

    private BigDecimal sumByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(t -> t.getType() == type)
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long countByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(t -> t.getType() == type)
                .count();
    }

    private boolean matchesTransactionFilter(Transaction transaction, TransactionFilterRequest filter) {
        if (filter.getAccountId() != null && !filter.getAccountId().isBlank()) {
            if (!filter.getAccountId().equals(transaction.getFromAccountId())
                    && !filter.getAccountId().equals(transaction.getToAccountId())) {
                return false;
            }
        }

        if (filter.getFromAccountId() != null && !filter.getFromAccountId().isBlank()
                && !filter.getFromAccountId().equals(transaction.getFromAccountId())) {
            return false;
        }

        if (filter.getToAccountId() != null && !filter.getToAccountId().isBlank()
                && !filter.getToAccountId().equals(transaction.getToAccountId())) {
            return false;
        }

        if (filter.getType() != null && filter.getType() != transaction.getType()) {
            return false;
        }

        if (filter.getStatus() != null && filter.getStatus() != transaction.getStatus()) {
            return false;
        }

        if (filter.getStartDate() != null) {
            if (transaction.getCreatedAt() == null || transaction.getCreatedAt().isBefore(filter.getStartDate())) {
                return false;
            }
        }

        if (filter.getEndDate() != null) {
            if (transaction.getCreatedAt() == null || transaction.getCreatedAt().isAfter(filter.getEndDate())) {
                return false;
            }
        }

        if (filter.getMinAmount() != null) {
            if (transaction.getAmount() == null || transaction.getAmount().compareTo(filter.getMinAmount()) < 0) {
                return false;
            }
        }

        if (filter.getMaxAmount() != null) {
            if (transaction.getAmount() == null || transaction.getAmount().compareTo(filter.getMaxAmount()) > 0) {
                return false;
            }
        }

        if (filter.getDescription() != null && !filter.getDescription().isBlank()) {
            String currentDescription = transaction.getDescription();
            if (currentDescription == null || !currentDescription.toLowerCase(Locale.ROOT)
                    .contains(filter.getDescription().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        if (filter.getReference() != null && !filter.getReference().isBlank()) {
            if (!filter.getReference().equals(transaction.getReference())) {
                return false;
            }
        }

        if (filter.getCreatedBy() != null && !filter.getCreatedBy().isBlank()
                && !filter.getCreatedBy().equals(transaction.getCreatedBy())) {
            return false;
        }

        return true;
    }

    private Comparator<Transaction> buildTransactionSortComparator(Sort sort) {
        Comparator<Transaction> defaultComparator =
                Comparator.comparing(Transaction::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

        if (sort.isUnsorted()) {
            return defaultComparator;
        }

        Comparator<Transaction> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<Transaction> fieldComparator = switch (order.getProperty()) {
                case "amount" -> Comparator.comparing(
                        Transaction::getAmount, Comparator.nullsLast(BigDecimal::compareTo));
                case "type" -> Comparator.comparing(
                        Transaction::getType, Comparator.nullsLast((a, b) -> a.compareTo(b)));
                case "status" -> Comparator.comparing(
                        Transaction::getStatus, Comparator.nullsLast((a, b) -> a.compareTo(b)));
                case "transactionId" -> Comparator.comparing(
                        Transaction::getTransactionId, Comparator.nullsLast(String::compareTo));
                case "fromAccountId" -> Comparator.comparing(
                        Transaction::getFromAccountId, Comparator.nullsLast(String::compareTo));
                case "toAccountId" -> Comparator.comparing(
                        Transaction::getToAccountId, Comparator.nullsLast(String::compareTo));
                case "createdAt" -> Comparator.comparing(
                        Transaction::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
                default -> Comparator.comparing(
                        Transaction::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
            };

            if (order.isDescending()) {
                fieldComparator = fieldComparator.reversed();
            }

            comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
        }

        return comparator != null ? comparator : defaultComparator;
    }

    private boolean isExternalAccount(String accountId) {
        return accountId != null && "EXTERNAL".equalsIgnoreCase(accountId.trim());
    }
    
    private TransactionResponse mapToResponse(Transaction transaction) {
        String reversalLinkId = transaction.getReversalTransactionId();
        if (reversalLinkId == null && transaction.getType() == TransactionType.REVERSAL) {
            reversalLinkId = transaction.getOriginalTransactionId();
        }

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
                .idempotencyKey(transaction.getIdempotencyKey())
                .processingState(transaction.getProcessingState() != null
                        ? transaction.getProcessingState().name() : null)
                .originalTransactionId(transaction.getOriginalTransactionId())
                .reversalTransactionId(reversalLinkId)
                .reversedAt(transaction.getReversedAt())
                .reversedBy(transaction.getReversedBy())
                .reversalReason(transaction.getReversalReason())
                .build();
    }
}
