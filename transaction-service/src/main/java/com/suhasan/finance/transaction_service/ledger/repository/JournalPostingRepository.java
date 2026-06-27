package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.JournalPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalPostingRepository extends JpaRepository<JournalPosting, UUID> {
    List<JournalPosting> findByJournalIdOrderByPostingSequence(UUID journalId);
}
