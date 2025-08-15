package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.dto.DepositRequest;
import com.suhasan.finance.transaction_service.dto.WithdrawalRequest;
import com.suhasan.finance.transaction_service.dto.ReversalRequest;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransactionFilterRequest;
import com.suhasan.finance.transaction_service.dto.TransactionStatsResponse;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {
    
    private final TransactionService transactionService;
    
    /**
     * Process a transfer between accounts
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> processTransfer(
            @Valid @RequestBody TransferRequest request,
            Authentication authentication) {
        
        log.info("Processing transfer request from {} to {} for amount {}", 
                request.getFromAccountId(), request.getToAccountId(), request.getAmount());
        
        String userId = authentication.getName();
        TransactionResponse response = transactionService.processTransfer(request, userId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Process a deposit to an account
     */
    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> processDeposit(
            @Valid @RequestBody DepositRequest request,
            Authentication authentication) {
        
        log.info("Processing deposit to account {} for amount {}", request.getAccountId(), request.getAmount());
        
        String userId = authentication.getName();
        TransactionResponse response = transactionService.processDeposit(
            request.getAccountId(), request.getAmount(), request.getDescription(), userId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Process a withdrawal from an account
     */
    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> processWithdrawal(
            @Valid @RequestBody WithdrawalRequest request,
            Authentication authentication) {
        
        log.info("Processing withdrawal from account {} for amount {}", request.getAccountId(), request.getAmount());
        
        String userId = authentication.getName();
        TransactionResponse response = transactionService.processWithdrawal(
            request.getAccountId(), request.getAmount(), request.getDescription(), userId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Get transaction by ID
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String transactionId) {
        log.debug("Retrieving transaction: {}", transactionId);
        
        TransactionResponse response = transactionService.getTransaction(transactionId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get transaction history for an account
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<Page<TransactionResponse>> getAccountTransactions(
            @PathVariable String accountId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        log.debug("Retrieving transactions for account: {}", accountId);
        
        Page<TransactionResponse> transactions = transactionService.getAccountTransactions(accountId, pageable);
        return ResponseEntity.ok(transactions);
    }
    
    /**
     * Get transaction history for the authenticated user
     */
    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getUserTransactions(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        String userId = authentication.getName();
        log.debug("Retrieving transactions for user: {}", userId);
        
        Page<TransactionResponse> transactions = transactionService.getUserTransactions(userId, pageable);
        return ResponseEntity.ok(transactions);
    }
    
    /**
     * Get transaction history for the authenticated user (alternative endpoint)
     */
    @GetMapping("/user")
    public ResponseEntity<Page<TransactionResponse>> getUserTransactionsAlternative(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        String userId = authentication.getName();
        log.debug("Retrieving transactions for user: {}", userId);
        
        Page<TransactionResponse> transactions = transactionService.getUserTransactions(userId, pageable);
        return ResponseEntity.ok(transactions);
    }
    
    /**
     * Get transactions by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByStatus(@PathVariable TransactionStatus status) {
        log.debug("Retrieving transactions with status: {}", status);
        
        List<TransactionResponse> transactions = transactionService.getTransactionsByStatus(status);
        return ResponseEntity.ok(transactions);
    }
    
    /**
     * Reverse a transaction
     */
    @PostMapping("/{transactionId}/reverse")
    public ResponseEntity<TransactionResponse> reverseTransaction(
            @PathVariable String transactionId,
            @Valid @RequestBody ReversalRequest request,
            Authentication authentication) {
        
        log.info("Reversing transaction {} with reason: {}", transactionId, request.getReason());
        
        String userId = authentication.getName();
        TransactionResponse response = transactionService.reverseTransaction(transactionId, request.getReason(), userId);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Check if a transaction has been reversed
     */
    @GetMapping("/{transactionId}/reversed")
    public ResponseEntity<Map<String, Object>> isTransactionReversed(@PathVariable String transactionId) {
        log.debug("Checking if transaction {} has been reversed", transactionId);
        
        boolean isReversed = transactionService.isTransactionReversed(transactionId);
        Map<String, Object> response = Map.of(
            "transactionId", transactionId,
            "isReversed", isReversed
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get reversal transactions for an original transaction
     */
    @GetMapping("/{transactionId}/reversals")
    public ResponseEntity<List<TransactionResponse>> getReversalTransactions(@PathVariable String transactionId) {
        log.debug("Retrieving reversal transactions for: {}", transactionId);
        
        List<TransactionResponse> reversals = transactionService.getReversalTransactions(transactionId);
        return ResponseEntity.ok(reversals);
    }
    
    /**
     * Get transaction statistics for an account
     */
    @GetMapping("/account/{accountId}/stats")
    public ResponseEntity<TransactionStatsResponse> getAccountTransactionStats(
            @PathVariable String accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.debug("Retrieving transaction statistics for account: {} from {} to {}", accountId, startDate, endDate);
        
        TransactionStatsResponse stats = transactionService.getAccountTransactionStats(accountId, startDate, endDate);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get transaction limits for the user
     */
    @GetMapping("/limits")
    public ResponseEntity<Map<String, Object>> getTransactionLimits(Authentication authentication) {
        String userId = authentication.getName();
        log.debug("Retrieving transaction limits for user: {}", userId);
        
        // Basic limits - can be enhanced later
        Map<String, Object> limits = Map.of(
            "dailyLimit", 10000.00,
            "monthlyLimit", 50000.00,
            "singleTransactionLimit", 10000.00,
            "currency", "USD"
        );
        
        return ResponseEntity.ok(limits);
    }
    
    /**
     * Search transactions with filters
     */
    @GetMapping("/search")
    public ResponseEntity<Page<TransactionResponse>> searchTransactions(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) String fromAccountId,
            @RequestParam(required = false) String toAccountId,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        log.debug("Searching transactions with filters");
        
        TransactionFilterRequest filter = TransactionFilterRequest.builder()
                .accountId(accountId)
                .type(type)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .minAmount(minAmount)
                .maxAmount(maxAmount)
                .description(description)
                .reference(reference)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .createdBy(authentication.getName())
                .build();
        
        Page<TransactionResponse> transactions = transactionService.searchTransactions(filter, pageable);
        return ResponseEntity.ok(transactions);
    }
    
    /**
     * Get transaction statistics for the authenticated user
     */
    @GetMapping("/user/stats")
    public ResponseEntity<TransactionStatsResponse> getUserTransactionStats(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        String userId = authentication.getName();
        log.debug("Retrieving transaction statistics for user: {} from {} to {}", userId, startDate, endDate);
        
        TransactionStatsResponse stats = transactionService.getUserTransactionStats(userId, startDate, endDate);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "transaction-service",
            "timestamp", java.time.Instant.now().toString()
        ));
    }
}