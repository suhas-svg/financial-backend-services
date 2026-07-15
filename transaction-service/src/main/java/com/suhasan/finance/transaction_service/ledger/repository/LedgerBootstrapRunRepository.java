package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.LedgerBootstrapRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerBootstrapRunRepository extends JpaRepository<LedgerBootstrapRun, String> {
}
