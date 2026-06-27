package com.suhasan.finance.transaction_service.ledger.web;

import java.util.UUID;
import java.util.List;

public record ReconciliationExceptionResponse(
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
}
