package com.suhasan.finance.transaction_service.ledger.service;

import java.util.List;

public record LedgerBootstrapResult(
        int importedAccounts,
        int reusedAccounts,
        int seededSystemAccounts,
        int openingJournals,
        List<String> currencies) {
}
