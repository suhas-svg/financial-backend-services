package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.ReconciliationExceptionNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationExceptionNoteRepository extends JpaRepository<ReconciliationExceptionNote, UUID> {
    Page<ReconciliationExceptionNote> findByExceptionIdOrderByCreatedAtDesc(UUID exceptionId, Pageable pageable);
    List<ReconciliationExceptionNote> findByExceptionIdOrderByCreatedAtDesc(UUID exceptionId);
}
