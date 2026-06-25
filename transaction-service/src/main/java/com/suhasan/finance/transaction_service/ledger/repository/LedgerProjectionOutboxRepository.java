package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.LedgerProjectionOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerProjectionOutboxRepository extends JpaRepository<LedgerProjectionOutbox, UUID> {

    @Query(value = """
            select * from ledger_projection_outbox
            where delivered_at is null
              and next_attempt_at <= :now
            order by next_attempt_at, created_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<LedgerProjectionOutbox> claimDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    long countByDeliveredAtIsNull();

    @Query("""
            select min(message.createdAt)
            from LedgerProjectionOutbox message
            where message.deliveredAt is null
            """)
    Optional<LocalDateTime> findOldestUndeliveredCreatedAt();
}
