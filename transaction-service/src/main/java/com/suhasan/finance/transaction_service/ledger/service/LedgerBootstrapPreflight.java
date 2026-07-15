package com.suhasan.finance.transaction_service.ledger.service;

import java.util.List;

public record LedgerBootstrapPreflight(
        boolean maintenanceModeConfirmed,
        boolean ready,
        boolean freshDatabase,
        List<String> requiredCurrencies,
        List<String> missingSystemAccounts,
        long legacyAccountCount,
        long ledgerAccountCount,
        long customerLedgerAccountCount,
        long journalCount,
        long transactionCount,
        List<String> blockers) {
}
