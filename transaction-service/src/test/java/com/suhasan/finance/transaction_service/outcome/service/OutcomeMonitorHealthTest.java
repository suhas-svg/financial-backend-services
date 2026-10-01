package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeDomainEvent;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenario;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeDomainEventRepository;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutcomeMonitorHealthTest {
    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

    @Mock OutcomeScenarioRepository scenarioRepository;
    @Mock OutcomeDomainEventRepository eventRepository;

    private OutcomeMonitorHealth health;
    private OutcomeScenario scenario;

    @BeforeEach
    void setUp() {
        health = new OutcomeMonitorHealth(scenarioRepository, eventRepository,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        scenario = OutcomeScenario.builder().scenarioId("s-1").userId("customer").name("Rent shield")
                .status("ACTIVE").currentVersion(2).currency("USD").timeZone("UTC").build();
        lenient().when(scenarioRepository.findById("s-1")).thenReturn(Optional.of(scenario));
    }

    @Test
    void backoffDoublesFromFiveMinutesAndIsCappedAtADay() {
        assertThat(OutcomeMonitorHealth.backoffAfter(1)).isEqualTo(Duration.ofMinutes(5));
        assertThat(OutcomeMonitorHealth.backoffAfter(2)).isEqualTo(Duration.ofMinutes(10));
        assertThat(OutcomeMonitorHealth.backoffAfter(3)).isEqualTo(Duration.ofMinutes(20));
        assertThat(OutcomeMonitorHealth.backoffAfter(9)).isEqualTo(Duration.ofMinutes(5 * 256));
        assertThat(OutcomeMonitorHealth.backoffAfter(10)).isEqualTo(Duration.ofHours(24));
        assertThat(OutcomeMonitorHealth.backoffAfter(10_000)).as("no overflow").isEqualTo(Duration.ofHours(24));
    }

    @Test
    void aFailureIsCountedAndPostponesTheNextAttempt() {
        health.recordFailure("s-1", new IllegalStateException("Account service unavailable: 500"));

        assertThat(scenario.getMonitorFailureCount()).isEqualTo(1);
        assertThat(scenario.getMonitorNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(scenario.getMonitorLastError()).isEqualTo("Account service unavailable: 500");
        assertThat(scenario.getMonitorDegradedAt()).isNull();
        verify(eventRepository, never()).save(any());
    }

    @Test
    void theFifthConsecutiveFailureMarksTheScenarioDegradedOnce() {
        for (int i = 0; i < 7; i++) {
            health.recordFailure("s-1", new IllegalStateException("still failing"));
        }

        assertThat(scenario.getMonitorFailureCount()).isEqualTo(7);
        assertThat(scenario.getMonitorDegradedAt()).isEqualTo(NOW);
        ArgumentCaptor<OutcomeDomainEvent> event = ArgumentCaptor.forClass(OutcomeDomainEvent.class);
        verify(eventRepository, times(1)).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("MONITORING_DEGRADED");
        assertThat(event.getValue().getScenarioId()).isEqualTo("s-1");
        assertThat(event.getValue().getUserId()).isEqualTo("customer");
        assertThat(event.getValue().getScenarioVersion()).isEqualTo(2);
        assertThat(event.getValue().getFieldsJson()).contains("\"consecutiveFailures\":5").contains("still failing");
    }

    @Test
    void aSuccessClearsTheFailureStateAndRecordsRecoveryOnlyIfItWasDegraded() {
        for (int i = 0; i < 5; i++) {
            health.recordFailure("s-1", new IllegalStateException("down"));
        }

        health.recordSuccess(scenario);

        assertThat(scenario.getMonitorFailureCount()).isZero();
        assertThat(scenario.getMonitorNextAttemptAt()).isNull();
        assertThat(scenario.getMonitorLastError()).isNull();
        assertThat(scenario.getMonitorDegradedAt()).isNull();
        ArgumentCaptor<OutcomeDomainEvent> events = ArgumentCaptor.forClass(OutcomeDomainEvent.class);
        verify(eventRepository, times(2)).save(events.capture());
        assertThat(events.getAllValues()).extracting(OutcomeDomainEvent::getEventType)
                .containsExactly("MONITORING_DEGRADED", "MONITORING_RECOVERED");
        assertThat(events.getAllValues().get(1).getFieldsJson()).contains("\"failuresBeforeRecovery\":5");
    }

    @Test
    void aSuccessAfterAFewFailuresResetsWithoutARecoveryEvent() {
        health.recordFailure("s-1", new IllegalStateException("blip"));

        health.recordSuccess(scenario);

        assertThat(scenario.getMonitorFailureCount()).isZero();
        assertThat(scenario.getMonitorNextAttemptAt()).isNull();
        verify(eventRepository, never()).save(any());
    }

    @Test
    void aHealthyScenarioIsLeftUntouched() {
        health.recordSuccess(scenario);

        assertThat(scenario.getMonitorFailureCount()).isZero();
        verify(eventRepository, never()).save(any());
    }

    @Test
    void errorsAreCollapsedToOneLineAndBounded() {
        health.recordFailure("s-1", new IllegalStateException("line one\n   line two\t" + "x".repeat(600)));
        assertThat(scenario.getMonitorLastError()).hasSize(500).startsWith("line one line two x").doesNotContain("\n");

        health.recordFailure("s-1", new IllegalStateException());
        assertThat(scenario.getMonitorLastError()).isEqualTo("IllegalStateException");
    }

    @Test
    void aScenarioThatNoLongerExistsIsIgnored() {
        lenient().when(scenarioRepository.findById("gone")).thenReturn(Optional.empty());

        health.recordFailure("gone", new IllegalStateException("x"));

        verify(eventRepository, never()).save(any());
    }
}
