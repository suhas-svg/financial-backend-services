package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, String>, JpaSpecificationExecutor<AuditLogEntry> {

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByOutcomeAndCreatedAtBetween(String outcome, LocalDateTime from, LocalDateTime to);

    long countByActionAndCreatedAtBetween(String action, LocalDateTime from, LocalDateTime to);

    long countByEventTypeAndCreatedAtBetween(String eventType, LocalDateTime from, LocalDateTime to);

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
