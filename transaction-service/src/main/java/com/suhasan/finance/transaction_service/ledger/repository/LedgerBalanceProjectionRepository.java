package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.LedgerBalanceProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LedgerBalanceProjectionRepository extends JpaRepository<LedgerBalanceProjection, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LedgerBalanceProjection p where p.ledgerAccountId in :ids order by p.ledgerAccountId")
    List<LedgerBalanceProjection> lockAllOrdered(@Param("ids") Collection<UUID> ids);
}
