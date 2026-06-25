package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.ReconciliationExceptionNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationExceptionNoteRepository extends JpaRepository<ReconciliationExceptionNote, UUID> {
    List<ReconciliationExceptionNote> findByExceptionIdOrderByCreatedAtDesc(UUID exceptionId);
}
