package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccount;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerBalanceProjection;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerProjectionOutboxRepository;
import com.suhasan.finance.transaction_service.ledger.service.AccountLedgerResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountClosureService {
    private final JdbcTemplate jdbc;
    private final ResilientAccountServiceClient accountServiceClient;
    private final AccountLedgerResolver accountLedgerResolver;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBalanceProjectionRepository projectionRepository;
    private final LedgerProjectionOutboxRepository projectionOutboxRepository;

    @Transactional
    public AccountDto close(String accountId, String actor, boolean privileged, String reason) {
        AccountDto account = accountServiceClient.getAccountInternal(accountId);
        if (account == null) throw new IllegalArgumentException("Account not found");
        if (!privileged && !actor.equals(account.getOwnerId())) {
            throw new AccessDeniedException("Account does not belong to the authenticated user");
        }
        accountLedgerResolver.resolveCustomerAccount(accountId, account);
        LedgerAccount ledgerAccount = ledgerAccountRepository.findByExternalAccountId(accountId)
                .orElseThrow(() -> new IllegalStateException("Account has no authoritative ledger account"));
        if (ledgerAccount.getAccountKind() != LedgerAccountKind.CUSTOMER) {
            throw new IllegalStateException("Only customer ledger accounts can be closed");
        }
        LedgerBalanceProjection projection = projectionRepository
                .lockAllOrdered(java.util.List.of(ledgerAccount.getLedgerAccountId()))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Account has no authoritative ledger projection"));
        requireZero(projection.getPostedBalance(), "posted");
        requireZero(projection.getPendingBalance(), "pending");
        requireZero(projection.getAvailableBalance(), "available");
        if (projectionOutboxRepository.countByExternalAccountIdAndDeliveredAtIsNull(accountId) > 0) {
            throw new IllegalStateException("Account closure is blocked until its ledger projection is synchronized");
        }
        if (count("""
                SELECT COUNT(*) FROM scheduled_transfers
                 WHERE (from_account_id=? OR to_account_id=?)
                   AND status IN ('ACTIVE','PAUSED')
                """, accountId, accountId) > 0) {
            throw new IllegalStateException("Account has active or paused scheduled transfers");
        }
        if (count("""
                SELECT COUNT(*) FROM transaction_disputes d
                  JOIN transactions t ON t.transaction_id=d.transaction_id
                 WHERE (t.from_account_id=? OR t.to_account_id=?)
                   AND d.status IN ('OPEN','IN_REVIEW')
                """, accountId, accountId) > 0) {
            throw new IllegalStateException("Account has unresolved disputes");
        }
        if (count("""
                SELECT COUNT(*) FROM outcome_guardrail_policies
                 WHERE (funding_account_id=? OR protected_account_id=?)
                   AND status IN ('CONSENT_PENDING','ACTIVE','SUSPENDED')
                """, accountId, accountId) > 0) {
            throw new IllegalStateException("Account has active protective controls");
        }
        AccountDto closed = accountServiceClient.closeAccount(accountId, reason);
        ledgerAccount.close();
        ledgerAccountRepository.save(ledgerAccount);
        return closed;
    }

    private long count(String sql, String first, String second) {
        Long value = jdbc.queryForObject(sql, Long.class, first, second);
        return value == null ? 0 : value;
    }

    private void requireZero(java.math.BigDecimal value, String kind) {
        if (value == null || value.signum() != 0) {
            throw new IllegalStateException("Account closure requires zero " + kind + " balance");
        }
    }
}
