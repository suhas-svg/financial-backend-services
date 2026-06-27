package com.suhasan.finance.transaction_service.ledger.service;

import java.math.BigDecimal;

public record LedgerBootstrapAccountSnapshot(
        String externalAccountId,
        String ownerId,
        String currency,
        BigDecimal ledgerBalance,
        BigDecimal availableBalance,
        String status) {

    public LedgerBootstrapAccountSnapshot {
        if (isBlank(externalAccountId) || isBlank(ownerId) || isBlank(currency)) {
            throw new IllegalArgumentException("Account id, owner, and currency are required for ledger bootstrap");
        }
        currency = currency.trim().toUpperCase();
        ledgerBalance = ledgerBalance == null ? BigDecimal.ZERO : ledgerBalance;
        availableBalance = availableBalance == null ? ledgerBalance : availableBalance;
        status = isBlank(status) ? "ACTIVE" : status.trim().toUpperCase();
    }

    public boolean hasUnresolvedLegacyHold() {
        return ledgerBalance.compareTo(availableBalance) != 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
