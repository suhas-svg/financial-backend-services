package com.suhasan.finance.transaction_service.ledger.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/reconciliation")
public class AdminReconciliationController {

    private final AdminReconciliationQueryService reconciliationQueryService;

    public AdminReconciliationController(AdminReconciliationQueryService reconciliationQueryService) {
        this.reconciliationQueryService = reconciliationQueryService;
    }

    @GetMapping("/runs")
    public List<ReconciliationRunResponse> listRuns() {
        return reconciliationQueryService.listRuns();
    }

    @PostMapping("/runs")
    public ReconciliationRunResponse run(
            @RequestBody ReconciliationRunRequest request,
            Authentication authentication) {
        return reconciliationQueryService.runDaily(request.businessDate(), authentication.getName());
    }

    @GetMapping("/exceptions")
    public List<ReconciliationExceptionResponse> listExceptions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity) {
        return reconciliationQueryService.listExceptions(status, severity);
    }

    @GetMapping("/exceptions/{exceptionId}")
    public ReconciliationExceptionResponse getException(@PathVariable UUID exceptionId) {
        return reconciliationQueryService.getException(exceptionId);
    }

    @PatchMapping("/exceptions/{exceptionId}/status")
    public ReconciliationExceptionResponse updateStatus(
            @PathVariable UUID exceptionId,
            @RequestBody ReconciliationExceptionStatusRequest request,
            Authentication authentication) {
        return reconciliationQueryService.updateStatus(
                exceptionId,
                request.status(),
                request.note(),
                authentication.getName(),
                request.expectedVersion());
    }

    @PatchMapping("/exceptions/{exceptionId}/assignment")
    public ReconciliationExceptionResponse assign(
            @PathVariable UUID exceptionId,
            @RequestBody ReconciliationExceptionAssignmentRequest request,
            Authentication authentication) {
        return reconciliationQueryService.assignException(
                exceptionId,
                request.assignedTo(),
                authentication.getName(),
                request.expectedVersion());
    }

    @PostMapping("/exceptions/{exceptionId}/notes")
    public ReconciliationExceptionResponse addNote(
            @PathVariable UUID exceptionId,
            @RequestBody ReconciliationExceptionNoteRequest request,
            Authentication authentication) {
        return reconciliationQueryService.addNote(
                exceptionId,
                request.note(),
                authentication.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void badRequest() {
    }
}
