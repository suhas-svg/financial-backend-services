package com.suhasan.finance.transaction_service.ledger.service;

import java.time.LocalDate;

public record LedgerBootstrapCommand(
        String requestedBy,
        boolean enabled,
        boolean maintenanceMode,
        LocalDate businessDate) {

    public LedgerBootstrapCommand {
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("Bootstrap requester is required");
        }
        if (businessDate == null) {
            businessDate = LocalDate.now();
        }
    }

    public static LedgerBootstrapCommand disabled(String requestedBy) {
        return new LedgerBootstrapCommand(requestedBy, false, false, LocalDate.now());
    }

    public static LedgerBootstrapCommand enabled(String requestedBy, boolean maintenanceMode, LocalDate businessDate) {
        return new LedgerBootstrapCommand(requestedBy, true, maintenanceMode, businessDate);
    }
}
