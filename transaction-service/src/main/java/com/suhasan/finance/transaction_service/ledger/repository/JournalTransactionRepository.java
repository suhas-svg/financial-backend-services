package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.JournalTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalTransactionRepository extends JpaRepository<JournalTransaction, UUID> {
    Optional<JournalTransaction> findByIdempotencyScopeAndIdempotencyKey(String scope, String key);
    Optional<JournalTransaction> findByReversalOfJournalId(UUID originalJournalId);
    List<JournalTransaction> findAllByEffectiveDateLessThanEqual(LocalDate businessDate);
    List<JournalTransaction> findAllByEffectiveDateLessThan(LocalDate exclusiveEndDate);
}
