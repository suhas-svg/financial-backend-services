package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.*;
import com.suhasan.finance.transaction_service.ledger.service.LedgerReconciliationService;
import com.suhasan.finance.transaction_service.ledger.service.ReconciliationRunResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAdminReconciliationQueryServiceTest {
    private final LedgerReconciliationService reconciliation = mock(LedgerReconciliationService.class);
    private final ReconciliationRunRepository runs = mock(ReconciliationRunRepository.class);
    private final ReconciliationExceptionRepository exceptions = mock(ReconciliationExceptionRepository.class);
    private final ReconciliationExceptionNoteRepository notes = mock(ReconciliationExceptionNoteRepository.class);
    private final LedgerAccountRepository accounts = mock(LedgerAccountRepository.class);
    private final DefaultAdminReconciliationQueryService service =
            new DefaultAdminReconciliationQueryService(reconciliation, runs, exceptions, notes, accounts);

    @Test
    void listsAndRunsReconciliationInNewestFirstOrder() {
        ReconciliationRun older = run(LocalDateTime.parse("2026-07-25T00:00:00"));
        ReconciliationRun newer = run(LocalDateTime.parse("2026-07-26T00:00:00"));
        when(runs.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(newer, older)));
        assertThat(service.listRuns()).extracting(ReconciliationRunResponse::startedAt)
                .containsExactly(newer.getStartedAt(), older.getStartedAt());

        LocalDate day = LocalDate.of(2026, 7, 26);
        when(reconciliation.runDaily(day, "operator")).thenReturn(new ReconciliationRunResult(
                newer.getRunId(), day, ReconciliationRunStatus.COMPLETED, 0, 0));
        when(runs.findById(newer.getRunId())).thenReturn(Optional.of(newer));
        assertThat(service.runDaily(day, "operator").runId()).isEqualTo(newer.getRunId());
        when(runs.findById(newer.getRunId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.runDaily(day, "operator")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void queriesMapsAndMutatesExceptions() {
        UUID accountId = UUID.randomUUID();
        ReconciliationException exception = ReconciliationException.builder()
                .exceptionId(UUID.randomUUID()).checkCode(ReconciliationCheckCode.PROJECTION_RECOMPUTATION)
                .severity(ReconciliationSeverity.CRITICAL).fingerprint("fingerprint").summary("drift")
                .ledgerAccountId(accountId).currency("USD").expectedAmount(BigDecimal.ONE)
                .actualAmount(BigDecimal.TEN).deltaAmount(BigDecimal.valueOf(9))
                .status(ReconciliationExceptionStatus.OPEN)
                .createdAt(LocalDateTime.parse("2026-07-26T00:00:00"))
                .updatedAt(LocalDateTime.parse("2026-07-26T01:00:00")).version(2).build();
        LedgerAccount account = LedgerAccount.builder().ledgerAccountId(accountId)
                .externalAccountId("customer-account").build();
        ReconciliationExceptionNote note = ReconciliationExceptionNote.builder()
                .noteId(UUID.randomUUID()).exceptionId(exception.getExceptionId())
                .author("operator").note("reviewed").createdAt(LocalDateTime.now()).build();
        when(exceptions.findByStatusAndSeverity(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(exception)));
        when(exceptions.findByStatus(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(exceptions.findById(exception.getExceptionId())).thenReturn(Optional.of(exception));
        when(exceptions.findLatestRunId(exception.getExceptionId())).thenReturn(Optional.of(UUID.randomUUID()));
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(notes.findByExceptionIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(exception.getExceptionId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(note)));

        assertThat(service.listExceptions("OPEN", "CRITICAL")).singleElement()
                .extracting(ReconciliationExceptionResponse::externalAccountId).isEqualTo("customer-account");
        assertThat(service.listExceptions("RESOLVED", null)).isEmpty();
        assertThat(service.getException(exception.getExceptionId()).notes()).hasSize(1);
        assertThatThrownBy(() -> service.getException(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);

        when(reconciliation.updateExceptionStatus(exception.getExceptionId(),
                ReconciliationExceptionStatus.ACKNOWLEDGED, "note", "actor", 2)).thenReturn(exception);
        when(reconciliation.assignException(exception.getExceptionId(), "owner", "actor", 2)).thenReturn(exception);
        when(reconciliation.addExceptionNote(exception.getExceptionId(), "note", "actor")).thenReturn(exception);
        service.updateStatus(exception.getExceptionId(), "ACKNOWLEDGED", "note", "actor", 2);
        service.assignException(exception.getExceptionId(), "owner", "actor", 2);
        service.addNote(exception.getExceptionId(), "note", "actor");
        verify(reconciliation).addExceptionNote(exception.getExceptionId(), "note", "actor");
    }

    private ReconciliationRun run(LocalDateTime startedAt) {
        return ReconciliationRun.builder().runId(UUID.randomUUID()).businessDate(startedAt.toLocalDate())
                .reconciliationType(ReconciliationType.DAILY_LEDGER)
                .status(ReconciliationRunStatus.COMPLETED).requestedBy("operator")
                .startedAt(startedAt).completedAt(startedAt.plusMinutes(1)).build();
    }
}
