package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.ReconciliationExceptionRepository;
import com.suhasan.finance.transaction_service.ledger.repository.ReconciliationExceptionNoteRepository;
import com.suhasan.finance.transaction_service.ledger.repository.ReconciliationRunRepository;
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

    public DefaultAdminReconciliationQueryService(
            LedgerReconciliationService reconciliationService,
            ReconciliationRunRepository runRepository,
            ReconciliationExceptionRepository exceptionRepository,
            ReconciliationExceptionNoteRepository noteRepository) {
        this.reconciliationService = reconciliationService;
        this.runRepository = runRepository;
        this.exceptionRepository = exceptionRepository;
        this.noteRepository = noteRepository;
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
        return new ReconciliationRunResponse(
                result.runId(),
                result.businessDate(),
                ReconciliationType.DAILY_LEDGER.name(),
                result.status().name(),
                result.totalExceptions(),
                result.criticalExceptions());
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
                run.getTotalExceptions(),
                run.getCriticalExceptions());
    }

    private ReconciliationExceptionResponse toExceptionResponse(ReconciliationException exception) {
        return new ReconciliationExceptionResponse(
                exception.getExceptionId(),
                exception.getCheckCode().name(),
                exception.getSeverity().name(),
                exception.getStatus().name(),
                exception.getFingerprint(),
                exception.getSummary(),
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
