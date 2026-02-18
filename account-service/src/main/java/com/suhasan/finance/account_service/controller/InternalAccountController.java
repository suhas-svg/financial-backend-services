package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.BalanceOperationRequest;
import com.suhasan.finance.account_service.dto.BalanceOperationResponse;
import com.suhasan.finance.account_service.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/accounts")
public class InternalAccountController {

    private final AccountService accountService;

    @PutMapping("/{id}/balance")
    public ResponseEntity<Void> updateBalance(@PathVariable Long id,
                                              @Valid @RequestBody BalanceUpdateRequest request) {
        accountService.updateBalance(id, request.getBalance());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/balance-ops")
    public ResponseEntity<BalanceOperationResponse> applyBalanceOperation(
            @PathVariable Long id,
            @Valid @RequestBody BalanceOperationRequest request) {
        return ResponseEntity.ok(accountService.applyBalanceOperation(id, request));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceUpdateRequest {
        @NotNull(message = "Balance is required")
        private BigDecimal balance;
    }
}
