package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SyntheticFundingService {
    private final TransactionService transactionService;
    private final ResilientAccountServiceClient accountServiceClient;
    private final AuditService auditService;
    private final Environment environment;

    @Value("${controlled-beta.synthetic-funding.enabled:false}")
    private boolean enabled;

    public TransactionResponse fund(
            String accountId, BigDecimal amount, String reason, String idempotencyKey, String operator) {
        if (!enabled || environment.acceptsProfiles(Profiles.of("production", "prod"))) {
            throw new IllegalStateException("Synthetic funding is disabled in this runtime profile");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        AccountDto account = accountServiceClient.getAccountInternal(accountId);
        if (account == null || "CLOSED".equalsIgnoreCase(account.getStatus())) {
            throw new IllegalArgumentException("Eligible target account not found");
        }
        String safeReason = reason == null ? "controlled beta funding" : reason.trim();
        TransactionResponse response = transactionService.processDeposit(
                accountId, amount, "[SYNTHETIC] " + safeReason,
                "SYNTHETIC:" + idempotencyKey, account.getOwnerId(), "synthetic:" + idempotencyKey);
        auditService.logSecurityEvent("SYNTHETIC_FUNDING", operator,
                "transaction=" + response.getTransactionId() + " account=" + accountId,
                "operator-authenticated");
        return response;
    }
}
