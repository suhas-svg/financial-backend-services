package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.JournalStateEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalStateEventRepository extends JpaRepository<JournalStateEvent, UUID> {
    List<JournalStateEvent> findByJournalIdOrderByEventSequence(UUID journalId);
    Optional<JournalStateEvent> findFirstByJournalIdOrderByEventSequenceDesc(UUID journalId);
}
