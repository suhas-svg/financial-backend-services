package com.suhasan.finance.transaction_service.ledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerBootstrapRun;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBootstrapRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LedgerBootstrapAuditService {
    private final LedgerBootstrapRunRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void started(String runId, LedgerBootstrapCommand command, String mode) {
        repository.save(LedgerBootstrapRun.builder()
                .runId(runId).requestedBy(command.requestedBy()).requestedRole(command.requestedRole())
                .requestId(command.requestId()).mode(mode)
                .businessDate(command.businessDate()).maintenanceMode(command.maintenanceMode())
                .outcome("STARTED").startedAt(Instant.now()).build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeeded(String runId, LedgerBootstrapResult result) {
        LedgerBootstrapRun run = repository.findById(runId).orElseThrow();
        run.setOutcome("SUCCEEDED");
        run.setImportedAccounts(result.importedAccounts());
        run.setReusedAccounts(result.reusedAccounts());
        run.setSeededSystemAccounts(result.seededSystemAccounts());
        run.setOpeningJournals(result.openingJournals());
        run.setCurrenciesJson(json(result.currencies()));
        run.setCompletedAt(Instant.now());
        repository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(String runId, RuntimeException failure) {
        LedgerBootstrapRun run = repository.findById(runId).orElseThrow();
        run.setOutcome("FAILED");
        run.setFailureReason(sanitize(failure));
        run.setCompletedAt(Instant.now());
        repository.save(run);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize ledger bootstrap evidence", exception);
        }
    }

    private String sanitize(RuntimeException failure) {
        String value = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        value = value.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
