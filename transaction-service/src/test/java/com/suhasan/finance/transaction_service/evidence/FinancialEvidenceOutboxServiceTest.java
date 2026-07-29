package com.suhasan.finance.transaction_service.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.suhasan.finance.transaction_service.ledger.domain.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FinancialEvidenceOutboxServiceTest {

    @Test
    void ledgerLifecycleIntentPreservesSourceEventIdentityAndBoundedPayload() {
        FinancialEvidenceOutboxRepository repository = mock(FinancialEvidenceOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        FinancialEvidenceOutboxService service = new FinancialEvidenceOutboxService(repository, objectMapper);
        UUID journalId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        JournalTransaction journal = JournalTransaction.builder()
                .journalId(journalId)
                .journalReference("JRN-" + journalId)
                .journalType(JournalType.TRANSFER)
                .currency("USD")
                .effectiveDate(LocalDate.of(2026, 7, 29))
                .description("must not be copied")
                .correlationId("correlation-1")
                .createdBy("user-1")
                .createdAt(LocalDateTime.of(2026, 7, 29, 10, 0))
                .idempotencyScope("user-1:TRANSFER")
                .idempotencyKey("secret-request-key")
                .requestFingerprint("fingerprint")
                .build();
        JournalStateEvent state = JournalStateEvent.builder()
                .eventId(eventId)
                .journalId(journalId)
                .eventSequence(2)
                .state(JournalState.POSTED)
                .actor("worker")
                .reason("must not be copied")
                .createdAt(LocalDateTime.of(2026, 7, 29, 10, 1))
                .build();

        service.enqueueLedgerLifecycle(journal, state);

        ArgumentCaptor<FinancialEvidenceOutbox> captor =
                ArgumentCaptor.forClass(FinancialEvidenceOutbox.class);
        verify(repository).save(captor.capture());
        FinancialEvidenceOutbox event = captor.getValue();
        assertThat(event.getEventId()).isEqualTo(eventId);
        assertThat(event.getIdempotencyKey()).isEqualTo(eventId + ":ledger-lifecycle:v1");
        assertThat(event.getStatus()).isEqualTo(FinancialEvidenceStatus.PENDING);
        assertThat(event.getMaxAttempts()).isEqualTo(8);
        assertThat(event.getPayload())
                .contains("\"journalState\":\"POSTED\"", "\"eventSequence\":2")
                .doesNotContain("secret-request-key", "must not be copied", "fingerprint", "worker");
    }
}
