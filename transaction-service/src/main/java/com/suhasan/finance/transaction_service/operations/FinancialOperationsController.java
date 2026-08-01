package com.suhasan.finance.transaction_service.operations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/admin/financial-operations")
public class FinancialOperationsController {
    private final FinancialOperationsCoordinator coordinator;

    public FinancialOperationsController(FinancialOperationsCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @PostMapping("/daily-reconciliation")
    public ResponseEntity<FinancialOperationResult> daily(
            @Valid @RequestBody DailyRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        requireBoundedKey(idempotencyKey);
        return ResponseEntity.ok(coordinator.runDaily(
                request.businessDate(), authentication.getName(), request.reason()));
    }

    @PostMapping("/monthly-statement-close")
    public ResponseEntity<FinancialOperationResult> monthly(
            @Valid @RequestBody MonthlyRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        requireBoundedKey(idempotencyKey);
        return ResponseEntity.ok(coordinator.runMonthly(
                YearMonth.parse(request.period()), authentication.getName(), request.reason()));
    }

    private void requireBoundedKey(String value) {
        if (value == null || value.isBlank() || value.length() > 160) {
            throw new IllegalArgumentException("A bounded Idempotency-Key is required");
        }
    }

    public record DailyRequest(@NotNull LocalDate businessDate, @NotBlank String reason) {}
    public record MonthlyRequest(@NotBlank String period, @NotBlank String reason) {}
}
