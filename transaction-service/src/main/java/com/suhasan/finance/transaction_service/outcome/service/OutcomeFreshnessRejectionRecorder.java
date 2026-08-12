package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeDomainEvent;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeDomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutcomeFreshnessRejectionRecorder {
    private final OutcomeDomainEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String userId, String scenarioId, int scenarioVersion, String resultId,
                       String guardrailId, String dedupeKey, Map<String, ?> fields) {
        if (eventRepository.findByUserIdAndDedupeKey(userId, dedupeKey).isPresent()) return;
        eventRepository.save(OutcomeDomainEvent.builder()
                .eventId(UUID.randomUUID().toString()).eventType(ScenarioDivergedException.CODE)
                .userId(userId).scenarioId(scenarioId).scenarioVersion(scenarioVersion)
                .resultId(resultId).guardrailId(guardrailId).dedupeKey(dedupeKey)
                .fieldsJson(json(fields)).build());
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to serialize freshness evidence", ex); }
    }
}
