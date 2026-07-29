package com.suhasan.finance.transaction_service.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.ledger.domain.JournalStateEvent;
import com.suhasan.finance.transaction_service.ledger.domain.JournalTransaction;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FinancialEvidenceOutboxService {
    private static final int MAX_ATTEMPTS = 8;
    private final FinancialEvidenceOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public FinancialEvidenceOutboxService(
            FinancialEvidenceOutboxRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void enqueueLedgerLifecycle(JournalTransaction journal, JournalStateEvent stateEvent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("journalId", journal.getJournalId());
        payload.put("journalReference", journal.getJournalReference());
        payload.put("journalType", journal.getJournalType());
        payload.put("journalState", stateEvent.getState());
        payload.put("eventSequence", stateEvent.getEventSequence());
        payload.put("currency", journal.getCurrency());
        payload.put("effectiveDate", journal.getEffectiveDate());
        payload.put("correlationId", journal.getCorrelationId());
        payload.put("occurredAt", stateEvent.getCreatedAt());

        repository.save(FinancialEvidenceOutbox.create(
                stateEvent.getEventId(), "JOURNAL", journal.getJournalId().toString(),
                "JOURNAL_" + stateEvent.getState().name(),
                stateEvent.getEventId() + ":ledger-lifecycle:v1",
                serialize(payload), stateEvent.getCreatedAt(), MAX_ATTEMPTS));
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Financial evidence intent could not be serialized", exception);
        }
    }
}
