package com.suhasan.finance.transaction_service.ledger.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AdminReconciliationQueryService {
    List<ReconciliationRunResponse> listRuns();
    ReconciliationRunResponse runDaily(LocalDate businessDate, String requestedBy);
    List<ReconciliationExceptionResponse> listExceptions(String status, String severity);
    ReconciliationExceptionResponse getException(UUID exceptionId);
    ReconciliationExceptionResponse updateStatus(
            UUID exceptionId, String status, String note, String actor, long expectedVersion);
    ReconciliationExceptionResponse assignException(
            UUID exceptionId, String assignedTo, String actor, long expectedVersion);
    ReconciliationExceptionResponse addNote(UUID exceptionId, String note, String actor);
}
