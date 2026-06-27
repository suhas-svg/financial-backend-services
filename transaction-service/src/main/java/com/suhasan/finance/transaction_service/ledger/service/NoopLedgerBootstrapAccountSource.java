package com.suhasan.finance.transaction_service.ledger.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoopLedgerBootstrapAccountSource implements LedgerBootstrapAccountSource {
    @Override
    public List<LedgerBootstrapAccountSnapshot> fetchAccountsForBootstrap() {
        return List.of();
    }
}
