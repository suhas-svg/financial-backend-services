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
        List<String> blockers,
        String operatorId,
        String operatorRole,
        boolean operatorAuthorized,
        String requestId) {

    public LedgerBootstrapPreflight(boolean maintenanceModeConfirmed, boolean ready, boolean freshDatabase,
                                    List<String> requiredCurrencies, List<String> missingSystemAccounts,
                                    long legacyAccountCount, long ledgerAccountCount, long customerLedgerAccountCount,
                                    long journalCount, long transactionCount, List<String> blockers) {
        this(maintenanceModeConfirmed, ready, freshDatabase, requiredCurrencies, missingSystemAccounts,
                legacyAccountCount, ledgerAccountCount, customerLedgerAccountCount, journalCount,
                transactionCount, blockers, null, null, false, null);
    }
}
