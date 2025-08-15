package com.suhasan.finance.transaction_service.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionStatsResponse {
    
    private String accountId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    
    // Transaction counts
    private Long totalTransactions;
    private Long completedTransactions;
    private Long pendingTransactions;
    private Long failedTransactions;
    private Long reversedTransactions;
    
    // Transaction amounts
    private BigDecimal totalAmount;
    private BigDecimal totalIncoming;
    private BigDecimal totalOutgoing;
    private BigDecimal totalDeposits;
    private BigDecimal totalWithdrawals;
    private BigDecimal totalTransfers;
    
    // Average amounts
    private BigDecimal averageTransactionAmount;
    private BigDecimal largestTransaction;
    private BigDecimal smallestTransaction;
    
    // Transaction counts by type
    private Map<String, Long> transactionCountsByType;
    
    // Transaction amounts by type
    private Map<String, BigDecimal> transactionAmountsByType;
    
    // Daily/Monthly summaries
    private BigDecimal dailyTotal;
    private BigDecimal monthlyTotal;
    private Long dailyCount;
    private Long monthlyCount;
    
    // Success rate
    private Double successRate;
    
    // Currency
    @Builder.Default
    private String currency = "USD";
}