package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.ReconciliationRun;
import com.suhasan.finance.transaction_service.ledger.domain.ReconciliationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

    /**
     * Attempts to take the per-(business date, type) reconciliation lock.
     *
     * <p>Uses {@code pg_try_advisory_xact_lock} so a second concurrent run for the same business date
     * fails immediately instead of waiting, and so the lock is released automatically when the
     * surrounding transaction commits or rolls back. The previous default implementation returned a
     * hard-coded {@code true}, which meant the caller's guard could never fire and two daily
     * reconciliations could run concurrently against the same ledger state.
     */
    @Query(value = "select pg_try_advisory_xact_lock(hashtextextended(:lockKey, 0))", nativeQuery = true)
    Boolean tryAcquireDailyRunLock(@Param("lockKey") String lockKey);

    static String dailyRunLockKey(final LocalDate businessDate, final ReconciliationType reconciliationType) {
        return "reconciliation-run:" + reconciliationType.name() + ":" + businessDate;
    }
}
