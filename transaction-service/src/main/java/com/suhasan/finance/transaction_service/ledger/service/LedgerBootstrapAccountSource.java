package com.suhasan.finance.transaction_service.ledger.service;

import java.util.List;

public interface LedgerBootstrapAccountSource {
    List<LedgerBootstrapAccountSnapshot> fetchAccountsForBootstrap();
}
