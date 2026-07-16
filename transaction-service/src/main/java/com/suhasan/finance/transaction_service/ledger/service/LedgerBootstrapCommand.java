package com.suhasan.finance.transaction_service.ledger.service;

import java.time.LocalDate;

public record LedgerBootstrapCommand(
        String requestedBy,
        String requestedRole,
        String requestId,
        boolean enabled,
        boolean maintenanceMode,
        LocalDate businessDate) {

    public LedgerBootstrapCommand(String requestedBy, boolean enabled, boolean maintenanceMode, LocalDate businessDate) {
        this(requestedBy, "LEGACY_OPERATOR", "legacy-bootstrap", enabled, maintenanceMode, businessDate);
    }

    public LedgerBootstrapCommand {
        if (requestedBy == null || requestedBy.isBlank()) throw new IllegalArgumentException("Bootstrap requester is required");
        if (requestedRole == null || requestedRole.isBlank()) throw new IllegalArgumentException("Bootstrap requester role is required");
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("Bootstrap request ID is required");
        if (businessDate == null) businessDate = LocalDate.now();
    }

    public static LedgerBootstrapCommand disabled(String requestedBy) {
        return new LedgerBootstrapCommand(requestedBy, false, false, LocalDate.now());
    }

    public static LedgerBootstrapCommand enabled(String requestedBy, boolean maintenanceMode, LocalDate businessDate) {
        return new LedgerBootstrapCommand(requestedBy, true, maintenanceMode, businessDate);
    }
}
