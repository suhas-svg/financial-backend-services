package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.service.CustomerMonthlyStatementLineResult;
import com.suhasan.finance.transaction_service.ledger.service.CustomerMonthlyStatementResult;
import com.suhasan.finance.transaction_service.ledger.service.MonthlyStatementService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger/statements")
public class CustomerStatementController {

    private final MonthlyStatementService statementService;

    public CustomerStatementController(MonthlyStatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping
    public List<CustomerStatementResponse> list(Authentication authentication) {
        return statementService.listForOwner(authentication.getName()).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public CustomerStatementResponse generate(
            @RequestBody CustomerStatementGenerateRequest request,
            Authentication authentication) {
        return toResponse(statementService.generate(
                authentication.getName(),
                request.externalAccountId(),
                YearMonth.parse(request.yearMonth())));
    }

    @GetMapping("/{statementId}")
    public CustomerStatementResponse get(
            @PathVariable UUID statementId,
            Authentication authentication) {
        return toResponse(statementService.getForOwner(authentication.getName(), statementId));
    }

    @GetMapping(value = "/{statementId}/csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @PathVariable UUID statementId,
            Authentication authentication) {
        String csv = statementService.exportCsvForOwner(authentication.getName(), statementId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"statement-" + statementId + ".csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound() {
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void forbidden() {
    }

    private CustomerStatementResponse toResponse(CustomerMonthlyStatementResult statement) {
        return new CustomerStatementResponse(
                statement.statementId(),
                statement.externalAccountId(),
                statement.currency(),
                statement.periodStart(),
                statement.periodEnd(),
                statement.statementVersion(),
                statement.openingBalance(),
                statement.closingBalance(),
                statement.generatedAt(),
                statement.lines().stream().map(this::toLineResponse).toList());
    }

    private CustomerStatementLineResponse toLineResponse(CustomerMonthlyStatementLineResult line) {
        return new CustomerStatementLineResponse(
                line.lineId(),
                line.journalId(),
                line.lineSequence(),
                line.effectiveDate(),
                line.description(),
                line.amount(),
                line.runningBalance(),
                line.currency());
    }
}
