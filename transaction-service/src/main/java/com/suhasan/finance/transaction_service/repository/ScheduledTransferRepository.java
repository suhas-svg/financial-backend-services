package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.ScheduledTransfer;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, String>,
        JpaSpecificationExecutor<ScheduledTransfer> {

    /**
     * Locks up to {@code limit} due schedules for the caller's transaction, skipping rows another
     * replica's claim already holds. Replicas therefore take disjoint schedules instead of queueing
     * behind one another. The unique run row and idempotency key still make a duplicate impossible.
     */
    @Query(value = """
            select * from scheduled_transfers
            where status = 'ACTIVE'
              and next_run_at <= :now
            order by next_run_at asc
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<ScheduledTransfer> claimDueActive(@Param("now") Instant now, @Param("limit") int limit);

    Page<ScheduledTransfer> findByUserId(String userId, Pageable pageable);

    Optional<ScheduledTransfer> findByScheduleIdAndUserId(String scheduleId, String userId);

    List<ScheduledTransfer> findByUserIdAndStatusOrderByNextRunAtAsc(String userId, ScheduledTransferStatus status);
}
