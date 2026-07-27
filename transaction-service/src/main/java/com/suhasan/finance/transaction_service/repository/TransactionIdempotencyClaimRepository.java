package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaim;
import com.suhasan.finance.transaction_service.entity.TransactionIdempotencyClaimState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionIdempotencyClaimRepository
        extends JpaRepository<TransactionIdempotencyClaim, String> {

    Optional<TransactionIdempotencyClaim> findByUserIdAndIdempotencyKey(
            String userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TransactionIdempotencyClaim c "
            + "where c.userId=:userId and c.idempotencyKey=:idempotencyKey")
    Optional<TransactionIdempotencyClaim> lockByScope(
            @Param("userId") String userId, @Param("idempotencyKey") String idempotencyKey);

    List<TransactionIdempotencyClaim> findTop100ByStateInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<TransactionIdempotencyClaimState> states, LocalDateTime updatedAt);
}
