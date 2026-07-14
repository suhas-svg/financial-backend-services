package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.ReconciliationExceptionRepository;
import com.suhasan.finance.transaction_service.ledger.repository.ReconciliationExceptionNoteRepository;
import com.suhasan.finance.transaction_service.ledger.repository.ReconciliationRunRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.service.LedgerReconciliationService;
import com.suhasan.finance.transaction_service.ledger.service.ReconciliationRunResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class DefaultAdminReconciliationQueryService implements AdminReconciliationQueryService {

    private final LedgerReconciliationService reconciliationService;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationExceptionRepository exceptionRepository;
    private final ReconciliationExceptionNoteRepository noteRepository;
    private final LedgerAccountRepository accountRepository;

    public DefaultAdminReconciliationQueryService(
            LedgerReconciliationService reconciliationService,
            ReconciliationRunRepository runRepository,
            ReconciliationExceptionRepository exceptionRepository,
            ReconciliationExceptionNoteRepository noteRepository,
            LedgerAccountRepository accountRepository) {
        this.reconciliationService = reconciliationService;
        this.runRepository = runRepository;
        this.exceptionRepository = exceptionRepository;
        this.noteRepository = noteRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReconciliationRunResponse> listRuns() {
        return runRepository.findAll().stream()
                .sorted(Comparator.comparing(ReconciliationRun::getStartedAt).reversed())
                .map(this::toRunResponse)
                .toList();
    }

    @Override
    public ReconciliationRunResponse runDaily(LocalDate businessDate, String requestedBy) {
        ReconciliationRunResult result = reconciliationService.runDaily(businessDate, requestedBy);
        return runRepository.findById(result.runId())
                .map(this::toRunResponse)
                .orElseThrow(() -> new IllegalStateException("Completed reconciliation run was not persisted"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReconciliationExceptionResponse> listExceptions(String status, String severity) {
        return exceptionRepository.findAll().stream()
                .filter(exception -> status == null || exception.getStatus().name().equals(status))
                .filter(exception -> severity == null || exception.getSeverity().name().equals(severity))
                .sorted(Comparator.comparing(ReconciliationException::getUpdatedAt).reversed())
                .map(this::toExceptionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationExceptionResponse getException(UUID exceptionId) {
        return exceptionRepository.findById(exceptionId)
                .map(this::toExceptionResponse)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation exception not found"));
    }

    @Override
    public ReconciliationExceptionResponse updateStatus(
            UUID exceptionId, String status, String note, String actor, long expectedVersion) {
        ReconciliationException updated = reconciliationService.updateExceptionStatus(
                exceptionId,
                ReconciliationExceptionStatus.valueOf(status),
                note,
                actor,
                expectedVersion);
        return toExceptionResponse(updated);
    }

    @Override
    public ReconciliationExceptionResponse assignException(
            UUID exceptionId, String assignedTo, String actor, long expectedVersion) {
        ReconciliationException updated = reconciliationService.assignException(
                exceptionId,
                assignedTo,
                actor,
                expectedVersion);
        return toExceptionResponse(updated);
    }

    @Override
    public ReconciliationExceptionResponse addNote(UUID exceptionId, String note, String actor) {
        ReconciliationException updated = reconciliationService.addExceptionNote(exceptionId, note, actor);
        return toExceptionResponse(updated);
    }

    private ReconciliationRunResponse toRunResponse(ReconciliationRun run) {
        return new ReconciliationRunResponse(
                run.getRunId(),
                run.getBusinessDate(),
                run.getReconciliationType().name(),
                run.getStatus().name(),
                run.getRequestedBy(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getTotalExceptions(),
                run.getCriticalExceptions());
    }

    private ReconciliationExceptionResponse toExceptionResponse(ReconciliationException exception) {
        LedgerAccount ledgerAccount = exception.getLedgerAccountId() == null
                ? null
                : accountRepository.findById(exception.getLedgerAccountId()).orElse(null);
        return new ReconciliationExceptionResponse(
                exception.getExceptionId(),
                exception.getCheckCode().name(),
                exception.getSeverity().name(),
                exception.getStatus().name(),
                exception.getFingerprint(),
                exception.getSummary(),
                exceptionRepository.findLatestRunId(exception.getExceptionId()).orElse(null),
                exception.getJournalId(),
                exception.getLedgerAccountId(),
                ledgerAccount == null ? null : ledgerAccount.getExternalAccountId(),
                exception.getCurrency(),
                exception.getExpectedAmount(),
                exception.getActualAmount(),
                exception.getDeltaAmount(),
                exception.getCreatedAt(),
                exception.getAssignedTo(),
                exception.getResolutionNote(),
                noteRepository.findByExceptionIdOrderByCreatedAtDesc(exception.getExceptionId()).stream()
                        .map(note -> new ReconciliationExceptionNoteResponse(
                                note.getNoteId(),
                                note.getAuthor(),
                                note.getNote()))
                        .toList(),
                exception.getVersion());
    }
}
