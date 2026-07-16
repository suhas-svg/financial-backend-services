package com.suhasan.finance.transaction_service.ledger.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ledger.bootstrap.startup.enabled", havingValue = "true")
public class FreshDatabaseLedgerBootstrapRunner implements ApplicationRunner {
    private final LedgerBootstrapPreflightService preflightService;
    private final LedgerBootstrapCoordinator coordinator;

    @Value("${ledger.authoritative:true}")
    private boolean ledgerAuthoritative;

    @Value("${ledger.bootstrap.startup.maintenance-mode:false}")
    private boolean maintenanceMode;

    @Override
    public void run(ApplicationArguments args) {
        if (ledgerAuthoritative) {
            throw new IllegalStateException("Fresh-database ledger bootstrap requires ledger.authoritative=false");
        }
        LedgerBootstrapPreflight preflight = preflightService.inspect(maintenanceMode);
        if (!preflight.ready()) {
            throw new IllegalStateException("Fresh-database ledger bootstrap preflight failed: "
                    + String.join(", ", preflight.blockers()));
        }
        if (!preflight.freshDatabase()) {
            throw new IllegalStateException("Startup ledger bootstrap is restricted to a fresh financial database");
        }
        coordinator.bootstrap(new LedgerBootstrapCommand("startup-bootstrap", "SYSTEM_STARTUP",
                "startup:" + LocalDate.now(ZoneOffset.UTC), true, true, LocalDate.now(ZoneOffset.UTC)), "STARTUP_FRESH_DATABASE");
    }
}
