package com.suhasan.finance.transaction_service.ledger.web;

import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReconciliationExceptionResponse(
        UUID exceptionId,
        String checkCode,
        String severity,
        String status,
        String fingerprint,
        String summary,
        UUID runId,
        UUID journalId,
        UUID ledgerAccountId,
        String externalAccountId,
        String currency,
        BigDecimal expectedAmount,
        BigDecimal actualAmount,
        BigDecimal deltaAmount,
        LocalDateTime detectedAt,
        String assignedTo,
        String resolutionNote,
        List<ReconciliationExceptionNoteResponse> notes,
        long version) {
    public ReconciliationExceptionResponse(
            UUID exceptionId,
            String checkCode,
            String severity,
            String status,
            String fingerprint,
            String summary,
            String assignedTo,
            String resolutionNote,
            List<ReconciliationExceptionNoteResponse> notes,
            long version) {
        this(exceptionId, checkCode, severity, status, fingerprint, summary,
                null, null, null, null, null, null, null, null, null,
                assignedTo, resolutionNote, notes, version);
    }
}
