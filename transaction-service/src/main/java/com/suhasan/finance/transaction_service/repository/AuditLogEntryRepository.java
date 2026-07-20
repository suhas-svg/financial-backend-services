package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, String>, JpaSpecificationExecutor<AuditLogEntry> {

    @Query("""
            select new com.suhasan.finance.transaction_service.dto.AuditSummaryResponse(
                count(entry),
                coalesce(sum(case when entry.outcome = 'FAILURE' then 1 else 0 end), 0),
                coalesce(sum(case when entry.action = 'TRANSACTION_REVERSED' then 1 else 0 end), 0),
                coalesce(sum(case when entry.eventType = 'SECURITY' then 1 else 0 end), 0)
            )
            from AuditLogEntry entry
            where entry.createdAt between :from and :to
            """)
    com.suhasan.finance.transaction_service.dto.AuditSummaryResponse summarize(
            LocalDateTime from, LocalDateTime to);

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
