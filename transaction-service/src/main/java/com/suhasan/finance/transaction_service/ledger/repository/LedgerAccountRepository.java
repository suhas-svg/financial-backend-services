package com.suhasan.finance.transaction_service.ledger.repository;

import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccount;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findByExternalAccountId(String externalAccountId);
    List<LedgerAccount> findByExternalAccountIdIn(Collection<String> externalAccountIds);
    Optional<LedgerAccount> findByAccountKindAndCurrency(LedgerAccountKind accountKind, String currency);
    List<LedgerAccount> findByOwnerIdAndAccountKindOrderByExternalAccountIdAsc(
            String ownerId, LedgerAccountKind accountKind);
    long countByAccountKind(LedgerAccountKind accountKind);
}
