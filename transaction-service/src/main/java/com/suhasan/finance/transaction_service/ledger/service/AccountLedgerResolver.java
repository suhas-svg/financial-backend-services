package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountLedgerResolver {

    private final LedgerAccountRepository ledgerAccountRepository;

    public AccountLedgerResolver(LedgerAccountRepository ledgerAccountRepository) {
        this.ledgerAccountRepository = ledgerAccountRepository;
    }

    public UUID resolveCustomerAccount(String externalAccountId, AccountDto account) {
        return ledgerAccountRepository.findByExternalAccountId(externalAccountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ledger account not found for account: " + externalAccountId))
                .getLedgerAccountId();
    }

    public UUID resolveSystemAccount(LedgerAccountKind accountKind, String currency) {
        return ledgerAccountRepository.findByAccountKindAndCurrency(accountKind, currency)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ledger system account not found for " + accountKind + " " + currency))
                .getLedgerAccountId();
    }
}
