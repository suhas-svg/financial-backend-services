package com.suhasan.finance.account_service.repository;
import com.suhasan.finance.account_service.entity.SpendingLimitReservation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
public interface SpendingLimitReservationRepository extends JpaRepository<SpendingLimitReservation,Long> {
 boolean existsByAccountIdAndOperationTypeAndIdempotencyKey(Long accountId,String operationType,String idempotencyKey);
 Optional<SpendingLimitReservation> findByAccountIdAndOperationTypeAndIdempotencyKey(Long accountId,String operationType,String idempotencyKey);
 @Query("select coalesce(sum(r.amount),0) from SpendingLimitReservation r where r.accountId=:a and r.operationType=:t and r.usageDate=:d") BigDecimal used(@Param("a") Long a,@Param("t") String t,@Param("d") LocalDate d);
}
