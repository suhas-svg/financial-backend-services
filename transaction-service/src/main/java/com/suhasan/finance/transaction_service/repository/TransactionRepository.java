package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

       // Find transactions by account
       Page<Transaction> findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                     String fromAccountId, String toAccountId, Pageable pageable);

       // Find transactions by user (through account ownership)
       @Query("SELECT t FROM Transaction t WHERE t.createdBy = :userId ORDER BY t.createdAt DESC")
       Page<Transaction> findByCreatedByOrderByCreatedAtDesc(@Param("userId") String userId, Pageable pageable);

       Optional<Transaction> findFirstByCreatedByAndTypeAndIdempotencyKey(
                     String createdBy, TransactionType type, String idempotencyKey);

       // Pessimistic lock for reversal flow — prevents concurrent duplicate reversals
       // from both passing the isTransactionReversed() check simultaneously.
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT t FROM Transaction t WHERE t.transactionId = :transactionId")
       Optional<Transaction> findByIdWithLock(@Param("transactionId") String transactionId);

       // Find transactions by status
       List<Transaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status);

       // Count transactions by status
       Long countByStatus(TransactionStatus status);

       // Find transactions by type and date range
       List<Transaction> findByTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
                     TransactionType type, LocalDateTime startDate, LocalDateTime endDate);

       // Daily transaction sum for an account
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND t.type = :type " +
                     "AND t.status = 'COMPLETED' " +
                     "AND CAST(t.createdAt AS date) = CURRENT_DATE")
       BigDecimal getDailyTransactionSum(@Param("accountId") String accountId,
                     @Param("type") TransactionType type);

       // Monthly transaction sum for an account
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND t.type = :type " +
                     "AND t.status = 'COMPLETED' " +
                     "AND EXTRACT(YEAR FROM t.createdAt) = EXTRACT(YEAR FROM CURRENT_DATE) " +
                     "AND EXTRACT(MONTH FROM t.createdAt) = EXTRACT(MONTH FROM CURRENT_DATE)")
       BigDecimal getMonthlyTransactionSum(@Param("accountId") String accountId,
                     @Param("type") TransactionType type);

       // Daily transaction count for an account
       @Query("SELECT COUNT(t) FROM Transaction t " +
                     "WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND t.type = :type " +
                     "AND t.status = 'COMPLETED' " +
                     "AND CAST(t.createdAt AS date) = CURRENT_DATE")
       Long getDailyTransactionCount(@Param("accountId") String accountId,
                     @Param("type") TransactionType type);

       // Monthly transaction count for an account
       @Query("SELECT COUNT(t) FROM Transaction t " +
                     "WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND t.type = :type " +
                     "AND t.status = 'COMPLETED' " +
                     "AND EXTRACT(YEAR FROM t.createdAt) = EXTRACT(YEAR FROM CURRENT_DATE) " +
                     "AND EXTRACT(MONTH FROM t.createdAt) = EXTRACT(MONTH FROM CURRENT_DATE)")
       Long getMonthlyTransactionCount(@Param("accountId") String accountId,
                     @Param("type") TransactionType type);

       // Find pending transactions older than specified time
       @Query("SELECT t FROM Transaction t WHERE t.status = 'PROCESSING' AND t.createdAt < :cutoffTime")
       List<Transaction> findPendingTransactionsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

       // Find reversal transaction by original transaction ID
       @Query("SELECT t FROM Transaction t WHERE t.originalTransactionId = :originalTransactionId AND t.type = 'REVERSAL'")
       List<Transaction> findReversalsByOriginalTransactionId(
                     @Param("originalTransactionId") String originalTransactionId);

       // Check if transaction has been reversed
       @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.originalTransactionId = :transactionId AND t.type = 'REVERSAL' AND t.status = 'COMPLETED'")
       boolean isTransactionReversed(@Param("transactionId") String transactionId);

       // Find original transaction by reversal transaction ID
       @Query("SELECT t FROM Transaction t WHERE t.reversalTransactionId = :reversalTransactionId")
       Transaction findOriginalTransactionByReversalId(@Param("reversalTransactionId") String reversalTransactionId);

       // Advanced filtering queries
       @Query("SELECT t FROM Transaction t WHERE " +
                     "(:accountId IS NULL OR t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND (:type IS NULL OR t.type = :type) " +
                     "AND (:status IS NULL OR t.status = :status) " +
                     "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                     "AND (:endDate IS NULL OR t.createdAt <= :endDate) " +
                     "AND (:minAmount IS NULL OR t.amount >= :minAmount) " +
                     "AND (:maxAmount IS NULL OR t.amount <= :maxAmount) " +
                     "AND (:description IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', :description, '%'))) " +
                     "AND (:reference IS NULL OR t.reference = :reference) " +
                     "AND (:createdBy IS NULL OR t.createdBy = :createdBy) " +
                     "ORDER BY t.createdAt DESC")
       Page<Transaction> findTransactionsWithFilters(
                     @Param("accountId") String accountId,
                     @Param("type") TransactionType type,
                     @Param("status") TransactionStatus status,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("minAmount") BigDecimal minAmount,
                     @Param("maxAmount") BigDecimal maxAmount,
                     @Param("description") String description,
                     @Param("reference") String reference,
                     @Param("createdBy") String createdBy,
                     Pageable pageable);

       // Statistics queries
       @Query("SELECT COUNT(t) FROM Transaction t WHERE " +
                     "(:accountId IS NULL OR t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                     "AND (:endDate IS NULL OR t.createdAt <= :endDate)")
       Long countTransactionsByAccountAndDateRange(
                     @Param("accountId") String accountId,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT COUNT(t) FROM Transaction t WHERE " +
                     "(:accountId IS NULL OR t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND t.status = :status " +
                     "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                     "AND (:endDate IS NULL OR t.createdAt <= :endDate)")
       Long countTransactionsByAccountStatusAndDateRange(
                     @Param("accountId") String accountId,
                     @Param("status") TransactionStatus status,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE " +
                     "(:accountId IS NULL OR t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND t.status = 'COMPLETED' " +
                     "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                     "AND (:endDate IS NULL OR t.createdAt <= :endDate)")
       BigDecimal sumTransactionAmountsByAccountAndDateRange(
                     @Param("accountId") String accountId,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE " +
                     "t.toAccountId = :accountId " +
                     "AND t.status = 'COMPLETED' " +
                     "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                     "AND (:endDate IS NULL OR t.createdAt <= :endDate)")
       BigDecimal sumIncomingTransactionsByAccountAndDateRange(
                     @Param("accountId") String accountId,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE " +
                     "t.fromAccountId = :accountId " +
                     "AND t.status = 'COMPLETED' " +
                     "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                     "AND (:endDate IS NULL OR t.createdAt <= :endDate)")
       BigDecimal sumOutgoingTransactionsByAccountAndDateRange(
                     @Param("accountId") String accountId,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE " +
                     "(:accountId IS NULL OR t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND t.type = :type " +
                     "AND t.status = 'COMPLETED' " +
                     "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                     "AND (:endDate IS NULL OR t.createdAt <= :endDate)")
       BigDecimal sumTransactionAmountsByAccountTypeAndDateRange(
                     @Param("accountId") String accountId,
                     @Param("type") TransactionType type,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT COUNT(t) FROM Transaction t WHERE " +
                     "(:accountId IS NULL OR t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND t.type = :type " +
                     "AND t.status = 'COMPLETED' " +
                     "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                     "AND (:endDate IS NULL OR t.createdAt <= :endDate)")
       Long countTransactionsByAccountTypeAndDateRange(
                     @Param("accountId") String accountId,
                     @Param("type") TransactionType type,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT MAX(t.amount) FROM Transaction t WHERE " +
                     "(:accountId IS NULL OR t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND t.status = 'COMPLETED' " +
                     "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                     "AND (:endDate IS NULL OR t.createdAt <= :endDate)")
       BigDecimal findMaxTransactionAmount(
                     @Param("accountId") String accountId,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT MIN(t.amount) FROM Transaction t WHERE " +
                     "(:accountId IS NULL OR t.fromAccountId = :accountId OR t.toAccountId = :accountId) " +
                     "AND t.status = 'COMPLETED' " +
                     "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                     "AND (:endDate IS NULL OR t.createdAt <= :endDate)")
       BigDecimal findMinTransactionAmount(
                     @Param("accountId") String accountId,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);
}
