package com.suhasan.finance.transaction_service.evidence;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinancialEvidenceOutboxDispatcherTest {
    private FinancialEvidenceOutboxRepository outboxRepository;
    private FinancialEvidenceDeliveryRepository deliveryRepository;
    private SimpleMeterRegistry meterRegistry;
    private FinancialEvidenceOutboxDispatcher dispatcher;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(FinancialEvidenceOutboxRepository.class);
        deliveryRepository = mock(FinancialEvidenceDeliveryRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);
        now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        dispatcher = new FinancialEvidenceOutboxDispatcher(
                outboxRepository, deliveryRepository, meterRegistry, clock);
        when(outboxRepository.countByStatusIn(anyList())).thenReturn(0L);
        when(outboxRepository.countByStatus(any())).thenReturn(0L);
        when(outboxRepository.findOldestCreatedAtByStatusIn(anyList())).thenReturn(Optional.empty());
    }

    @Test
    void deliversEveryDestinationOnceAndMarksIntentDelivered() {
        FinancialEvidenceOutbox event = event(8);
        when(outboxRepository.claimDue(now, 100)).thenReturn(List.of(event));

        FinancialEvidenceOutboxDispatcher.DispatchSummary summary = dispatcher.dispatchDue(500);

        assertThat(summary).isEqualTo(new FinancialEvidenceOutboxDispatcher.DispatchSummary(1, 1, 0, 0));
        assertThat(event.getStatus()).isEqualTo(FinancialEvidenceStatus.DELIVERED);
        assertThat(event.getDeliveredAt()).isEqualTo(now);
        ArgumentCaptor<FinancialEvidenceDelivery> captor =
                ArgumentCaptor.forClass(FinancialEvidenceDelivery.class);
        verify(deliveryRepository, times(FinancialEvidenceDestination.values().length)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FinancialEvidenceDelivery::getDestination)
                .containsExactlyInAnyOrder(FinancialEvidenceDestination.values());
    }

    @Test
    void postCommitRecoverySkipsExistingReceiptsAndCompletesMissingDestinations() {
        FinancialEvidenceOutbox event = event(8);
        when(outboxRepository.claimDue(now, 10)).thenReturn(List.of(event));
        when(deliveryRepository.existsByEventIdAndDestination(
                event.getEventId(), FinancialEvidenceDestination.AUDIT_ENRICHMENT)).thenReturn(true);
        when(deliveryRepository.existsByEventIdAndDestination(
                event.getEventId(), FinancialEvidenceDestination.RISK_NOTIFICATION)).thenReturn(true);

        dispatcher.dispatchDue(10);

        ArgumentCaptor<FinancialEvidenceDelivery> captor =
                ArgumentCaptor.forClass(FinancialEvidenceDelivery.class);
        verify(deliveryRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FinancialEvidenceDelivery::getDestination)
                .containsExactlyInAnyOrder(
                        FinancialEvidenceDestination.ANALYTICS,
                        FinancialEvidenceDestination.NONCRITICAL_METRICS);
        assertThat(event.getStatus()).isEqualTo(FinancialEvidenceStatus.DELIVERED);
    }

    @Test
    void deliveryFailureUsesBoundedBackoffThenBecomesTerminal() {
        FinancialEvidenceOutbox event = event(2);
        when(outboxRepository.claimDue(now, 10)).thenReturn(List.of(event));
        when(deliveryRepository.save(any())).thenThrow(new IllegalStateException("provider token=secret 12345"));

        FinancialEvidenceOutboxDispatcher.DispatchSummary first = dispatcher.dispatchDue(10);
        FinancialEvidenceOutboxDispatcher.DispatchSummary second = dispatcher.dispatchDue(10);

        assertThat(first.failed()).isEqualTo(1);
        assertThat(second.terminalFailures()).isEqualTo(1);
        assertThat(event.getAttemptCount()).isEqualTo(2);
        assertThat(event.getStatus()).isEqualTo(FinancialEvidenceStatus.TERMINAL_FAILED);
        assertThat(event.getLastError()).doesNotContain("secret", "12345").contains("[redacted]", "[id]");
    }

    @Test
    void backlogAndTerminalStateAreObservable() {
        when(outboxRepository.claimDue(now, 1)).thenReturn(List.of());
        when(outboxRepository.countByStatusIn(anyList())).thenReturn(7L);
        when(outboxRepository.countByStatus(FinancialEvidenceStatus.TERMINAL_FAILED)).thenReturn(2L);
        when(outboxRepository.findOldestCreatedAtByStatusIn(anyList()))
                .thenReturn(Optional.of(now.minusMinutes(11)));

        dispatcher.dispatchDue(1);

        assertThat(meterRegistry.get("financial.evidence.outbox.backlog").gauge().value()).isEqualTo(7);
        assertThat(meterRegistry.get("financial.evidence.outbox.oldest_age_seconds").gauge().value())
                .isEqualTo(660);
        assertThat(meterRegistry.get("financial.evidence.outbox.terminal_failures").gauge().value())
                .isEqualTo(2);
    }

    private FinancialEvidenceOutbox event(int maxAttempts) {
        UUID eventId = UUID.randomUUID();
        return FinancialEvidenceOutbox.create(
                eventId, "JOURNAL", UUID.randomUUID().toString(), "JOURNAL_POSTED",
                eventId + ":ledger-lifecycle:v1", "{\"schemaVersion\":1}",
                now.minusSeconds(30), maxAttempts);
    }
}
