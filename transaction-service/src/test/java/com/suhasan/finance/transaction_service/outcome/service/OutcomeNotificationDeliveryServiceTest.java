package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.outcome.domain.*;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeNotificationDeliveryRepository;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutcomeNotificationDeliveryServiceTest {
    @Mock OutcomeNotificationDeliveryRepository repository;
    @Mock ResilientAccountServiceClient accountServiceClient;
    private OutcomeNotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new OutcomeNotificationDeliveryService(repository, accountServiceClient,
                new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "initialBackoffSeconds", 10L);
        ReflectionTestUtils.setField(service, "maxBackoffSeconds", 60L);
        ReflectionTestUtils.setField(service, "slaSeconds", 30L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void retriesWithBoundedBackoffThenDeliversOneDeterministicDedupeKey() {
        OutcomeNotificationDelivery delivery = enqueue();
        when(repository.lockById(delivery.getDeliveryId())).thenReturn(Optional.of(delivery));
        doThrow(new IllegalStateException("account-service unavailable"))
                .doNothing().when(accountServiceClient).createNotification(any());

        Instant firstAttempt = delivery.getCreatedAt().plusSeconds(1);
        service.dispatch(delivery.getDeliveryId(), firstAttempt);
        assertThat(delivery.getState()).isEqualTo("RETRY_SCHEDULED");
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(firstAttempt.plusSeconds(10));

        service.dispatch(delivery.getDeliveryId(), firstAttempt.plusSeconds(10));
        assertThat(delivery.getState()).isEqualTo("DELIVERED");
        assertThat(delivery.getAttemptCount()).isEqualTo(2);
        assertThat(delivery.getDedupeKey()).isEqualTo("outcome-protection:warning-1");
        verify(accountServiceClient, times(2)).createNotification(argThat(request ->
                request.getDeliveryId().equals(delivery.getDeliveryId())
                        && request.getDedupeKey().equals(delivery.getDedupeKey())));
    }

    @Test
    void terminalFailureAndSlaEscalationArePersisted() {
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        ReflectionTestUtils.setField(service, "slaSeconds", 0L);
        OutcomeNotificationDelivery delivery = enqueue();
        when(repository.lockById(delivery.getDeliveryId())).thenReturn(Optional.of(delivery));
        doThrow(new IllegalStateException("still unavailable")).when(accountServiceClient).createNotification(any());

        service.dispatch(delivery.getDeliveryId(), delivery.getCreatedAt().plusSeconds(1));

        assertThat(delivery.getState()).isEqualTo("TERMINAL_FAILED");
        assertThat(delivery.getTerminalAt()).isNotNull();
        assertThat(delivery.getSlaEscalatedAt()).isNotNull();
        assertThat(delivery.getLastError()).contains("still unavailable");
    }

    private OutcomeNotificationDelivery enqueue() {
        when(repository.findByWarningEventId("warning-1")).thenReturn(Optional.empty());
        OutcomeDomainEvent warning = OutcomeDomainEvent.builder().eventId("warning-1").build();
        OutcomeScenario scenario = OutcomeScenario.builder().scenarioId("scenario-1").userId("customer-1")
                .currency("INR").build();
        ForecastProof forecast = new ForecastProof(false, new BigDecimal("10000.00"),
                new BigDecimal("9000.00"), LocalDate.parse("2026-07-20"),
                new BigDecimal("8000.00"), new BigDecimal("8000.00"), List.of(), List.of());
        SimulationProof proof = new SimulationProof(forecast, null, null, 0, false);
        OutcomeNotificationDelivery delivery = service.enqueue(warning, scenario, proof);
        delivery.setCreatedAt(Instant.parse("2026-07-16T00:00:00Z"));
        delivery.setNextAttemptAt(delivery.getCreatedAt());
        return delivery;
    }
}
