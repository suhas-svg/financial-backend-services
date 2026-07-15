package com.suhasan.finance.transaction_service.ledger.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerBootstrapCoordinator {
    private final LedgerBootstrapService bootstrapService;
    private final LedgerBootstrapAuditService auditService;

    public LedgerBootstrapResult bootstrap(LedgerBootstrapCommand command, String mode) {
        String runId = UUID.randomUUID().toString();
        auditService.started(runId, command, mode);
        try {
            LedgerBootstrapResult result = bootstrapService.bootstrap(command);
            auditService.succeeded(runId, result);
            return result.withRunId(runId);
        } catch (RuntimeException failure) {
            auditService.failed(runId, failure);
            throw failure;
        }
    }
}
