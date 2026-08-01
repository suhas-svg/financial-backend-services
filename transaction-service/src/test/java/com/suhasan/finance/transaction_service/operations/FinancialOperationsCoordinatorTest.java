package com.suhasan.finance.transaction_service.operations;

import com.suhasan.finance.transaction_service.ledger.domain.ReconciliationRunStatus;
import com.suhasan.finance.transaction_service.ledger.service.LedgerReconciliationService;
import com.suhasan.finance.transaction_service.ledger.service.MonthlyStatementService;
import com.suhasan.finance.transaction_service.ledger.service.ReconciliationRunResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FinancialOperationsCoordinatorTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void dailyRunCreatesDurableClaimBeforeLedgerOnlyReconciliationAndCompletesEvidence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LedgerReconciliationService reconciliation = mock(LedgerReconciliationService.class);
        MonthlyStatementService statements = mock(MonthlyStatementService.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        LocalDate date = LocalDate.of(2026, 7, 28);
        when(reconciliation.runDaily(date, "operator")).thenReturn(new ReconciliationRunResult(
                UUID.randomUUID(), date, ReconciliationRunStatus.COMPLETED, 2, 1));
        var registry = new SimpleMeterRegistry();
        var coordinator = coordinator(jdbc, reconciliation, statements, registry);

        FinancialOperationResult result = coordinator.runDaily(date, "operator", "daily controlled-beta run");

        assertThat(result.executed()).isTrue();
        assertThat(result.evidence()).contains("ledgerMutation=false", "critical=1");
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeast(2)).update(sql.capture(), any(Object[].class));
        assertThat(sql.getAllValues().get(0)).contains("INSERT INTO financial_operation_runs");
        verify(reconciliation).runDaily(date, "operator");
        verifyNoInteractions(statements);
        assertThat(registry.get("financial.operations.completed").counter().count()).isEqualTo(1);
    }

    @Test
    void monthlyCloseUsesActiveLedgerAccountsAndRecordsNoBalanceMutation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LedgerReconciliationService reconciliation = mock(LedgerReconciliationService.class);
        MonthlyStatementService statements = mock(MonthlyStatementService.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("owner_id", "synthetic-owner", "external_account_id", "42")));
        var registry = new SimpleMeterRegistry();
        var coordinator = coordinator(jdbc, reconciliation, statements, registry);

        FinancialOperationResult result = coordinator.runMonthly(
                YearMonth.of(2026, 6), "operator", "monthly controlled-beta close");

        assertThat(result.executed()).isTrue();
        assertThat(result.processedItems()).isEqualTo(1);
        assertThat(result.evidence()).contains("source=immutable-posted-ledger", "balanceMutation=false");
        verify(statements).generate("synthetic-owner", "42", YearMonth.of(2026, 6));
        verifyNoInteractions(reconciliation);
    }

    @Test
    void failedDailyRunIsDurablyMarkedAndCountedForAlerting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LedgerReconciliationService reconciliation = mock(LedgerReconciliationService.class);
        MonthlyStatementService statements = mock(MonthlyStatementService.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        LocalDate date = LocalDate.of(2026, 7, 28);
        when(reconciliation.runDaily(date, "operator")).thenThrow(new IllegalStateException("synthetic failure"));
        var registry = new SimpleMeterRegistry();
        var coordinator = coordinator(jdbc, reconciliation, statements, registry);

        assertThatThrownBy(() -> coordinator.runDaily(date, "operator", "failure drill"))
                .isInstanceOf(IllegalStateException.class);

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeast(2)).update(sql.capture(), any(Object[].class));
        assertThat(sql.getAllValues()).anyMatch(value -> value.contains("status='FAILED'"));
        assertThat(registry.get("financial.operations.failed").counter().count()).isEqualTo(1);
        verifyNoInteractions(statements);
    }

    private FinancialOperationsCoordinator coordinator(
            JdbcTemplate jdbc,
            LedgerReconciliationService reconciliation,
            MonthlyStatementService statements,
            SimpleMeterRegistry registry) {
        return new FinancialOperationsCoordinator(jdbc, reconciliation, statements, registry, CLOCK);
    }
}
