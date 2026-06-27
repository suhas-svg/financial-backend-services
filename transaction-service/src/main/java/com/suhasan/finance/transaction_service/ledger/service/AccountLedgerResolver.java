package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AccountLedgerResolver {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBalanceProjectionRepository projectionRepository;

    public AccountLedgerResolver(
            LedgerAccountRepository ledgerAccountRepository,
            LedgerBalanceProjectionRepository projectionRepository) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.projectionRepository = projectionRepository;
    }

    public UUID resolveCustomerAccount(String externalAccountId, AccountDto account) {
        return ledgerAccountRepository.findByExternalAccountId(externalAccountId)
                .orElseGet(() -> createCustomerAccount(externalAccountId, account))
                .getLedgerAccountId();
    }

    public UUID resolveSystemAccount(LedgerAccountKind accountKind, String currency) {
        return ledgerAccountRepository.findByAccountKindAndCurrency(accountKind, currency)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ledger system account not found for " + accountKind + " " + currency))
                .getLedgerAccountId();
    }

    private LedgerAccount createCustomerAccount(String externalAccountId, AccountDto account) {
        if (account == null) {
            throw new IllegalArgumentException("Ledger account not found for account: " + externalAccountId);
        }
        if (account.getOwnerId() == null || account.getOwnerId().isBlank()) {
            throw new IllegalArgumentException("Account owner is required for ledger account: " + externalAccountId);
        }

        UUID ledgerAccountId = UUID.randomUUID();
        String currency = account.getCurrency() == null || account.getCurrency().isBlank()
                ? "USD"
                : account.getCurrency().trim().toUpperCase();
        LedgerAccount ledgerAccount = ledgerAccountRepository.save(LedgerAccount.builder()
                .ledgerAccountId(ledgerAccountId)
                .accountKind(LedgerAccountKind.CUSTOMER)
                .externalAccountId(externalAccountId)
                .ownerId(account.getOwnerId())
                .currency(currency)
                .status(statusFor(account))
                .createdAt(LocalDateTime.now())
                .build());
        projectionRepository.save(LedgerBalanceProjection.open(
                ledgerAccount.getLedgerAccountId(),
                account.ledgerBalanceOrBalance()));
        return ledgerAccount;
    }

    private LedgerAccountStatus statusFor(AccountDto account) {
        if ("CLOSED".equalsIgnoreCase(account.getStatus()) || Boolean.FALSE.equals(account.getActive())) {
            return LedgerAccountStatus.CLOSED;
        }
        return LedgerAccountStatus.ACTIVE;
    }
}
