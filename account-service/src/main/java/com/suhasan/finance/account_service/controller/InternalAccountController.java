package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AccountResponse;
import com.suhasan.finance.account_service.dto.BalanceOperationRequest;
import com.suhasan.finance.account_service.dto.BalanceOperationResponse;
import com.suhasan.finance.account_service.dto.DebitHoldRequest;
import com.suhasan.finance.account_service.dto.DebitHoldResponse;
import com.suhasan.finance.account_service.dto.LedgerProjectionUpdateRequest;
import com.suhasan.finance.account_service.dto.SpendingLimitDtos;
import com.suhasan.finance.account_service.service.AccountService;
import com.suhasan.finance.account_service.service.SpendingLimitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/accounts")
public class InternalAccountController {
    private final AccountService accountService;

    @Autowired(required = false)
    private SpendingLimitService spendingLimitService;

    @PostMapping("/{id}/spending-limit-reservations")
    public ResponseEntity<SpendingLimitDtos.ReserveResponse> reserveLimit(
            @PathVariable final Long id,
            @Valid @RequestBody final SpendingLimitDtos.ReserveRequest request) {
        return ResponseEntity.ok(spendingLimitService.reserve(id, request));
    }

    @GetMapping("/{id}/spending-limit-reservations/{operationType}/{idempotencyKey}")
    public ResponseEntity<SpendingLimitDtos.ReserveResponse> lookupLimitReservation(
            @PathVariable final Long id,
            @PathVariable final String operationType,
            @PathVariable final String idempotencyKey,
            @RequestParam final String userId) {
        return ResponseEntity.ok(spendingLimitService.lookup(id, operationType, idempotencyKey, userId));
    }

    @PostMapping("/{id}/spending-limit-reservations/{reservationId}/consume")
    public ResponseEntity<SpendingLimitDtos.ReserveResponse> consumeLimitReservation(
            @PathVariable final Long id,
            @PathVariable final Long reservationId,
            @Valid @RequestBody final SpendingLimitDtos.ReservationTransitionRequest request) {
        return ResponseEntity.ok(spendingLimitService.consume(id, reservationId, request));
    }

    @PostMapping("/{id}/spending-limit-reservations/{reservationId}/release")
    public ResponseEntity<SpendingLimitDtos.ReserveResponse> releaseLimitReservation(
            @PathVariable final Long id,
            @PathVariable final Long reservationId,
            @Valid @RequestBody final SpendingLimitDtos.ReservationTransitionRequest request) {
        return ResponseEntity.ok(spendingLimitService.release(id, reservationId, request));
    }

    @PostMapping("/{id}/spending-limit-reservations/{reservationId}/reconciliation-required")
    public ResponseEntity<SpendingLimitDtos.ReserveResponse> requireLimitReservationReconciliation(
            @PathVariable final Long id,
            @PathVariable final Long reservationId,
            @Valid @RequestBody final SpendingLimitDtos.ReservationTransitionRequest request) {
        return ResponseEntity.ok(spendingLimitService.requireReconciliation(id, reservationId, request));
    }

    @DeleteMapping("/{id}/spending-limit-reservations/{operationType}/{idempotencyKey}")
    public ResponseEntity<Void> releaseLimit(
            @PathVariable final Long id,
            @PathVariable final String operationType,
            @PathVariable final String idempotencyKey,
            @RequestParam final String userId) {
        spendingLimitService.release(id, operationType, idempotencyKey, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/spending-limit-audit")
    public List<SpendingLimitDtos.AuditResponse> spendingLimitAudit() {
        return spendingLimitService.auditEvents();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> get(@PathVariable final Long id) {
        return ResponseEntity.ok(accountService.toResponse(accountService.findById(id)));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<AccountResponse> close(@PathVariable final Long id, @RequestParam final String reason) {
        return ResponseEntity.ok(accountService.toResponse(accountService.close(id, reason, "transaction-service")));
    }

    @PostMapping("/{id}/balance-ops")
    public ResponseEntity<BalanceOperationResponse> applyBalanceOperation(
            @PathVariable final Long id, @Valid @RequestBody final BalanceOperationRequest request) {
        return ResponseEntity.ok(accountService.applyBalanceOperation(id, request));
    }

    @PutMapping("/{id}/ledger-projection")
    public ResponseEntity<AccountResponse> updateLedgerProjection(
            @PathVariable final Long id, @Valid @RequestBody final LedgerProjectionUpdateRequest request) {
        return ResponseEntity.ok(accountService.applyLedgerProjection(id, request));
    }

    @PostMapping("/{id}/holds")
    public ResponseEntity<DebitHoldResponse> placeDebitHold(
            @PathVariable final Long id, @Valid @RequestBody final DebitHoldRequest request) {
        return ResponseEntity.ok(accountService.placeDebitHold(id, request));
    }

    @PostMapping("/{id}/holds/{holdId}/capture")
    public ResponseEntity<DebitHoldResponse> captureDebitHold(
            @PathVariable final Long id, @PathVariable final String holdId,
            @Valid @RequestBody final HoldTransitionRequest request) {
        return ResponseEntity.ok(accountService.captureDebitHold(
                id, holdId, request.getTransactionId(), request.getReason()));
    }

    @PostMapping("/{id}/holds/{holdId}/release")
    public ResponseEntity<DebitHoldResponse> releaseDebitHold(
            @PathVariable final Long id, @PathVariable final String holdId,
            @Valid @RequestBody final HoldTransitionRequest request) {
        return ResponseEntity.ok(accountService.releaseDebitHold(
                id, holdId, request.getTransactionId(), request.getReason()));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldTransitionRequest {
        @NotNull(message = "Transaction ID is required")
        private String transactionId;
        private String reason;
    }
}
