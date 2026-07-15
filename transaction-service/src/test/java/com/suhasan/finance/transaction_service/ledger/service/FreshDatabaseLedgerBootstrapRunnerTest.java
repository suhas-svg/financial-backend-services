package com.suhasan.finance.transaction_service.ledger.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FreshDatabaseLedgerBootstrapRunnerTest {
    @Mock LedgerBootstrapPreflightService preflightService;
    @Mock LedgerBootstrapCoordinator coordinator;

    @Test
    void refusesStartupBootstrapWhileLedgerAuthorityIsEnabled() {
        FreshDatabaseLedgerBootstrapRunner runner = new FreshDatabaseLedgerBootstrapRunner(preflightService, coordinator);
        ReflectionTestUtils.setField(runner, "ledgerAuthoritative", true);
        ReflectionTestUtils.setField(runner, "maintenanceMode", true);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ledger.authoritative=false");
        verifyNoInteractions(preflightService, coordinator);
    }

    @Test
    void refusesStartupAutomationForAUsedDatabase() {
        FreshDatabaseLedgerBootstrapRunner runner = new FreshDatabaseLedgerBootstrapRunner(preflightService, coordinator);
        ReflectionTestUtils.setField(runner, "ledgerAuthoritative", false);
        ReflectionTestUtils.setField(runner, "maintenanceMode", true);
        when(preflightService.inspect(true)).thenReturn(new LedgerBootstrapPreflight(true, true, false,
                List.of("INR"), List.of(), 0, 1, 1, 1, 0, List.of()));

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("restricted to a fresh financial database");
        verifyNoInteractions(coordinator);
    }
}
