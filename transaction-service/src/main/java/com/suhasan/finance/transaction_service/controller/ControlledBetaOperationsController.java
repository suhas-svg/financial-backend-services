package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.service.AccountClosureService;
import com.suhasan.finance.transaction_service.service.SyntheticFundingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/controlled-beta")
@RequiredArgsConstructor
public class ControlledBetaOperationsController {
    private final SyntheticFundingService syntheticFundingService;
    private final AccountClosureService accountClosureService;

    @PostMapping("/synthetic-funding")
    public ResponseEntity<TransactionResponse> fund(
            @Valid @RequestBody SyntheticFundingRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(syntheticFundingService.fund(
                request.accountId(), request.amount(), request.reason(), idempotencyKey, authentication.getName()));
    }

    @PostMapping("/accounts/{accountId}/close")
    public ResponseEntity<AccountDto> close(
            @PathVariable String accountId,
            @Valid @RequestBody AccountClosureRequest request,
            Authentication authentication) {
        boolean privileged = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(accountClosureService.close(
                accountId, authentication.getName(), privileged, request.reason()));
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Synthetic funding requires an operator");
        }
    }

    public record SyntheticFundingRequest(
            @NotBlank String accountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String reason) {}

    public record AccountClosureRequest(@NotBlank String reason) {}
}
