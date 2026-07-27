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

    @Query(value = "select coalesce(sum(amount),0) from spending_limit_reservations "
            + "where account_id=:a and operation_type=:t and usage_date=:d "
            + "and state in ('RESERVED','CONSUMED','RECONCILIATION_REQUIRED')",
            nativeQuery = true)
    BigDecimal used(@Param("a") Long accountId, @Param("t") String operationType,
                    @Param("d") LocalDate usageDate);
}
