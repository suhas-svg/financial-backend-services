package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.SpendingLimitReservation;
import com.suhasan.finance.account_service.entity.SpendingLimitReservationState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpendingLimitReservationRepository extends JpaRepository<SpendingLimitReservation, Long> {
    boolean existsByAccountIdAndOperationTypeAndIdempotencyKey(
            Long accountId, String operationType, String idempotencyKey);

    Optional<SpendingLimitReservation> findByAccountIdAndOperationTypeAndIdempotencyKey(
            Long accountId, String operationType, String idempotencyKey);

    List<SpendingLimitReservation> findByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(
            Long accountId, String idempotencyKey);

    Optional<SpendingLimitReservation> findFirstByAccountIdAndIdempotencyKeyOrderByCreatedAtAsc(
            Long accountId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SpendingLimitReservation r where r.reservationId=:reservationId and r.accountId=:accountId")
    Optional<SpendingLimitReservation> lockByReservationIdAndAccountId(
            @Param("reservationId") Long reservationId, @Param("accountId") Long accountId);

    Optional<SpendingLimitReservation> findFirstByTransactionCorrelationOrderByCreatedAtAsc(
            String transactionCorrelation);

    List<SpendingLimitReservation> findTop100ByStateAndExpiresAtBeforeOrderByExpiresAtAsc(
            SpendingLimitReservationState state, LocalDateTime expiresAt);

    @Query("select coalesce(sum(r.amount),0) from SpendingLimitReservation r "
            + "where r.accountId=:a and r.operationType=:t and r.usageDate=:d "
            + "and r.state in (com.suhasan.finance.account_service.entity.SpendingLimitReservationState.RESERVED, "
            + "com.suhasan.finance.account_service.entity.SpendingLimitReservationState.CONSUMED, "
            + "com.suhasan.finance.account_service.entity.SpendingLimitReservationState.RECONCILIATION_REQUIRED)")
    BigDecimal used(@Param("a") Long accountId, @Param("t") String operationType, @Param("d") LocalDate usageDate);
}
