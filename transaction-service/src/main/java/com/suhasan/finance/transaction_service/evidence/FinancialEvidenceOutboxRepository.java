package com.suhasan.finance.transaction_service.evidence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialEvidenceOutboxRepository extends JpaRepository<FinancialEvidenceOutbox, UUID> {

    @Query(value = """
            select * from financial_evidence_outbox
            where status in ('PENDING', 'RETRY_SCHEDULED')
              and next_attempt_at <= :now
            order by next_attempt_at, created_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<FinancialEvidenceOutbox> claimDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    long countByStatusIn(List<FinancialEvidenceStatus> statuses);

    long countByStatus(FinancialEvidenceStatus status);

    @Query("""
            select min(event.createdAt)
            from FinancialEvidenceOutbox event
            where event.status in :statuses
            """)
    Optional<LocalDateTime> findOldestCreatedAtByStatusIn(
            @Param("statuses") List<FinancialEvidenceStatus> statuses);
}
