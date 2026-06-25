package com.suhasan.finance.transaction_service.ledger.web;

import java.util.List;
import java.util.UUID;

public interface CustomerLedgerQueryService {
    List<LedgerAccountSummaryResponse> listAccounts(String customerId);

    LedgerAccountSummaryResponse getBalance(String customerId, String externalAccountId);

    List<LedgerAccountSummaryResponse> getBalances(String customerId, List<String> externalAccountIds);

    CustomerJournalResponse getJournal(String customerId, UUID journalId);
}
