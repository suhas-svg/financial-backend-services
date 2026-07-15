package com.suhasan.finance.transaction_service.ledger.service;

import java.util.List;

public record LedgerBootstrapResult(
        String runId,
        int importedAccounts,
        int reusedAccounts,
        int seededSystemAccounts,
        int openingJournals,
        List<String> currencies) {
    public LedgerBootstrapResult(int importedAccounts, int reusedAccounts, int seededSystemAccounts,
                                 int openingJournals, List<String> currencies) {
        this(null, importedAccounts, reusedAccounts, seededSystemAccounts, openingJournals, currencies);
    }

    public LedgerBootstrapResult withRunId(String value) {
        return new LedgerBootstrapResult(value, importedAccounts, reusedAccounts, seededSystemAccounts, openingJournals, currencies);
    }
}
