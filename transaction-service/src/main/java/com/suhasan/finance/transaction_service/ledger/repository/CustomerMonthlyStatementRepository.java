package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.CustomerMonthlyStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerMonthlyStatementRepository extends JpaRepository<CustomerMonthlyStatement, UUID> {

    Optional<CustomerMonthlyStatement> findFirstByOwnerIdAndExternalAccountIdAndPeriodStartAndPeriodEndOrderByStatementVersionDesc(
            String ownerId,
            String externalAccountId,
            LocalDate periodStart,
            LocalDate periodEnd);

    Page<CustomerMonthlyStatement> findByOwnerIdOrderByPeriodStartDescExternalAccountIdAscStatementVersionDesc(
            String ownerId, Pageable pageable);

    default Optional<CustomerMonthlyStatement> findLatestByOwnerAndAccountAndPeriod(
            String ownerId,
            String externalAccountId,
            LocalDate periodStart,
            LocalDate periodEnd) {
        return findFirstByOwnerIdAndExternalAccountIdAndPeriodStartAndPeriodEndOrderByStatementVersionDesc(
                ownerId,
                externalAccountId,
                periodStart,
                periodEnd);
    }
}
