package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.ReconciliationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Query("""
            select exception from ReconciliationException exception
            where exception.checkCode = :checkCode
              and exception.status not in (
                com.suhasan.finance.transaction_service.ledger.domain.ReconciliationExceptionStatus.RESOLVED,
                com.suhasan.finance.transaction_service.ledger.domain.ReconciliationExceptionStatus.WAIVED)
            """)
    List<ReconciliationException> findActiveByCheckCode(
            @Param("checkCode") com.suhasan.finance.transaction_service.ledger.domain.ReconciliationCheckCode checkCode);

    @Modifying
    @Query(value = """
            insert into reconciliation_run_exceptions (run_id, exception_id, first_seen_in_run)
            values (:runId, :exceptionId, :firstSeen)
            on conflict (run_id, exception_id) do nothing
            """, nativeQuery = true)
    void linkToRun(
            @Param("runId") UUID runId,
            @Param("exceptionId") UUID exceptionId,
            @Param("firstSeen") boolean firstSeen);

    @Query(value = """
            select run_id from reconciliation_run_exceptions
            where exception_id = :exceptionId
            order by created_at desc
            limit 1
            """, nativeQuery = true)
    Optional<UUID> findLatestRunId(@Param("exceptionId") UUID exceptionId);
}
