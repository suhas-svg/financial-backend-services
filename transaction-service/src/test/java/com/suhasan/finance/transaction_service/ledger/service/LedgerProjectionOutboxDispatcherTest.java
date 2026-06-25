package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerProjectionOutbox;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerProjectionOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerProjectionOutboxDispatcherTest {

    @Mock private LedgerProjectionOutboxRepository outboxRepository;
    @Mock private ResilientAccountServiceClient accountServiceClient;

    private Clock clock;
    private SimpleMeterRegistry meterRegistry;
    private LedgerProjectionOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-25T03:30:00Z"), ZoneOffset.UTC);
        meterRegistry = new SimpleMeterRegistry();
        dispatcher = new LedgerProjectionOutboxDispatcher(
                outboxRepository, accountServiceClient, meterRegistry, clock);
    }

    @Test
    void dispatchDueDeliversProjectionAndMarksMessageDelivered() {
        LedgerProjectionOutbox message = outbox("101", 7L);
        when(outboxRepository.claimDue(any(LocalDateTime.class), eq(10))).thenReturn(List.of(message));
        when(accountServiceClient.applyLedgerProjection(eq("101"), any()))
                .thenReturn(new ResilientAccountServiceClient.LedgerProjectionUpdateResponse(
                        101L, new BigDecimal("125.00"), new BigDecimal("-10.00"),
                        new BigDecimal("115.00"), "USD", 7L, message.getSourceEventId().toString()));
        when(outboxRepository.countByDeliveredAtIsNull()).thenReturn(2L);
        when(outboxRepository.findOldestUndeliveredCreatedAt()).thenReturn(Optional.of(
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusSeconds(90)));

        LedgerProjectionOutboxDispatcher.DispatchSummary summary = dispatcher.dispatchDue(10);

        assertThat(summary.attempted()).isEqualTo(1);
        assertThat(summary.delivered()).isEqualTo(1);
        assertThat(message.getDeliveredAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        assertThat(message.getLastError()).isNull();
        assertThat(meterRegistry.get("ledger.projection_outbox.backlog").gauge().value()).isEqualTo(2.0);
        assertThat(meterRegistry.get("ledger.projection_outbox.oldest_age_seconds").gauge().value()).isEqualTo(90.0);
        verify(accountServiceClient).applyLedgerProjection(eq("101"), argThat(request ->
                request.version() == 7L
                        && request.currency().equals("USD")
                        && request.sourceEventId().equals(message.getSourceEventId().toString())));
    }

    @Test
    void transientFailureSchedulesRetryWithSanitizedError() {
        LedgerProjectionOutbox message = outbox("101", 7L);
        when(outboxRepository.claimDue(any(LocalDateTime.class), eq(10))).thenReturn(List.of(message));
        when(accountServiceClient.applyLedgerProjection(eq("101"), any()))
                .thenThrow(new RuntimeException("connection timed out for account 101 with token secret"));

        LedgerProjectionOutboxDispatcher.DispatchSummary summary = dispatcher.dispatchDue(10);

        assertThat(summary.attempted()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(message.getAttemptCount()).isEqualTo(1);
        assertThat(message.getDeliveredAt()).isNull();
        assertThat(message.getNextAttemptAt()).isAfter(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        assertThat(message.getLastError()).doesNotContain("101").doesNotContain("secret");
    }

    @Test
    void terminalCurrencyConflictIsMarkedTerminalAndNotRetried() {
        LedgerProjectionOutbox message = outbox("101", 7L);
        when(outboxRepository.claimDue(any(LocalDateTime.class), eq(10))).thenReturn(List.of(message));
        when(accountServiceClient.applyLedgerProjection(eq("101"), any()))
                .thenThrow(ResilientAccountServiceClient.LedgerProjectionDeliveryException.terminal(
                        "currency mismatch for account 101"));

        LedgerProjectionOutboxDispatcher.DispatchSummary summary = dispatcher.dispatchDue(10);

        assertThat(summary.terminalFailures()).isEqualTo(1);
        assertThat(message.getDeliveredAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        assertThat(message.getLastError()).startsWith("TERMINAL:");
        assertThat(message.getLastError()).doesNotContain("101");
    }

    @Test
    void oneAccountFailureDoesNotBlockOtherDueMessages() {
        LedgerProjectionOutbox failing = outbox("101", 7L);
        LedgerProjectionOutbox succeeding = outbox("202", 3L);
        when(outboxRepository.claimDue(any(LocalDateTime.class), eq(10))).thenReturn(List.of(failing, succeeding));
        when(accountServiceClient.applyLedgerProjection(eq("101"), any()))
                .thenThrow(new RuntimeException("account service unavailable"));
        when(accountServiceClient.applyLedgerProjection(eq("202"), any()))
                .thenReturn(new ResilientAccountServiceClient.LedgerProjectionUpdateResponse(
                        202L, new BigDecimal("50.00"), BigDecimal.ZERO,
                        new BigDecimal("50.00"), "USD", 3L, succeeding.getSourceEventId().toString()));

        LedgerProjectionOutboxDispatcher.DispatchSummary summary = dispatcher.dispatchDue(10);

        assertThat(summary.attempted()).isEqualTo(2);
        assertThat(summary.delivered()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(failing.getDeliveredAt()).isNull();
        assertThat(succeeding.getDeliveredAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private LedgerProjectionOutbox outbox(String externalAccountId, long projectionVersion) {
        return LedgerProjectionOutbox.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                externalAccountId,
                UUID.randomUUID(),
                projectionVersion,
                new BigDecimal("125.00"),
                new BigDecimal("-10.00"),
                new BigDecimal("115.00"),
                "USD",
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusMinutes(5));
    }
}
