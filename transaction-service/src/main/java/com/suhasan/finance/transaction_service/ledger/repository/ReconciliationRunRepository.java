package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.ReconciliationRun;
import com.suhasan.finance.transaction_service.ledger.domain.ReconciliationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {
    default boolean tryAcquireDailyRunLock(LocalDate businessDate, ReconciliationType reconciliationType) {
        return true;
    }
}
