package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.ReconciliationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ReconciliationExceptionRepository extends JpaRepository<ReconciliationException, UUID> {
    @Query("""
            select exception from ReconciliationException exception
            where exception.fingerprint = :fingerprint
              and exception.status not in (
                com.suhasan.finance.transaction_service.ledger.domain.ReconciliationExceptionStatus.RESOLVED,
                com.suhasan.finance.transaction_service.ledger.domain.ReconciliationExceptionStatus.WAIVED)
            """)
    Optional<ReconciliationException> findOpenByFingerprint(String fingerprint);

    default void linkExistingToRun(ReconciliationException exception, UUID runId) {
        // Run/exception link persistence is added with the reconciliation schema.
    }
}
