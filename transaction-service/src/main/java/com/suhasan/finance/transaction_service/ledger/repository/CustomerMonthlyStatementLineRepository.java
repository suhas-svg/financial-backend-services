package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.CustomerMonthlyStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerMonthlyStatementLineRepository extends JpaRepository<CustomerMonthlyStatementLine, UUID> {
    List<CustomerMonthlyStatementLine> findByStatementIdOrderByLineSequence(UUID statementId);
}
