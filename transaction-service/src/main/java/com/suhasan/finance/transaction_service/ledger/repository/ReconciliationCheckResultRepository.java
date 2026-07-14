package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.ReconciliationCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationCheckResultRepository extends JpaRepository<ReconciliationCheckResult, UUID> {
}
