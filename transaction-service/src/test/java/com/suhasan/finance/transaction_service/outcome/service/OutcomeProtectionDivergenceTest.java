package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.entity.ScheduledTransfer;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferType;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccount;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountStatus;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerBalanceProjection;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeDomainEvent;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeNotificationDelivery;
import com.suhasan.finance.transaction_service.outcome.fx.FxRateQuote;
import com.suhasan.finance.transaction_service.outcome.fx.OutcomeFxConverter;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenario;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenarioVersion;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeSimulationResult;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeDomainEventRepository;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeGuardrailDraftRepository;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeScenarioRepository;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeScenarioVersionRepository;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeSimulationResultRepository;
import com.suhasan.finance.transaction_service.repository.ScheduledTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutcomeProtectionDivergenceTest {
    @Mock OutcomeScenarioRepository scenarioRepository;
    @Mock OutcomeScenarioVersionRepository versionRepository;
    @Mock OutcomeSimulationResultRepository resultRepository;
    @Mock OutcomeGuardrailDraftRepository guardrailRepository;
    @Mock OutcomeDomainEventRepository eventRepository;
    @Mock LedgerAccountRepository ledgerAccountRepository;
    @Mock LedgerBalanceProjectionRepository projectionRepository;
    @Mock ScheduledTransferRepository scheduledTransferRepository;
    @Mock OutcomeNotificationDeliveryService notificationDeliveryService;
    @Mock OutcomeGuardrailService outcomeGuardrailService;
    @Mock ResilientAccountServiceClient accountServiceClient;

    private OutcomeProtectionService service;
    private final Map<String, OutcomeDomainEvent> events = new HashMap<>();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OutcomeFxConverter fxConverter = new OutcomeFxConverter((quote, base, time) -> new FxRateQuote(
                quote, base, BigDecimal.ONE, Instant.EPOCH, "TEST", "Deterministic test rate", false, "REJECT"));
        OutcomeAuthoritativeSourceService sourceService = new OutcomeAuthoritativeSourceService(
                ledgerAccountRepository, projectionRepository, scheduledTransferRepository,
                new OutcomeScheduledTransferForecaster(), fxConverter, accountServiceClient, objectMapper);
        service = new OutcomeProtectionService(scenarioRepository, versionRepository, resultRepository,
                guardrailRepository, eventRepository, new OutcomeSimulationEngine(3, 100), sourceService,
                notificationDeliveryService, outcomeGuardrailService, objectMapper);

        OutcomeScenario scenario = OutcomeScenario.builder()
                .scenarioId("scenario-1").userId("customer-1").name("INR shield").status("ACTIVE")
                .currentVersion(1).currency("INR").timeZone("Asia/Kolkata")
                .lastSourceFingerprint("saved-safe").lastProtectionState("SAFE").build();
        OutcomeScenarioVersion version = OutcomeScenarioVersion.builder()
                .versionId("version-1").scenarioId("scenario-1").scenarioVersion(1)
                .horizonStart(LocalDate.of(2026, 7, 16)).horizonDays(7)
                .protectedMinimum(new BigDecimal("9000.00"))
                .accountIdsJson("[\"10\"]").assumptionsJson("[]").shocksJson("[]")
                .ledgerSnapshotJson("[]").scheduleSnapshotJson("[]")
                .sourceFingerprint("saved-safe").mutationIdempotencyKey("create-key")
                .requestFingerprint("request-fingerprint").build();
        OutcomeSimulationResult savedResult = OutcomeSimulationResult.builder()
                .resultId("result-1").scenarioId("scenario-1").scenarioVersion(1)
                .baselineSafe(true).baselineLowestBalance(new BigDecimal("10000.00"))
                .proofJson("{}").repairJson("{}").resultFingerprint("result-fingerprint").build();

        UUID ledgerId = UUID.randomUUID();
        LedgerAccount ledgerAccount = LedgerAccount.builder()
                .ledgerAccountId(ledgerId).accountKind(LedgerAccountKind.CUSTOMER)
                .externalAccountId("10").ownerId("customer-1").currency("INR")
                .status(LedgerAccountStatus.ACTIVE).createdAt(LocalDateTime.now()).build();
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(ledgerId, new BigDecimal("10000.00"));
        ScheduledTransfer schedule = ScheduledTransfer.builder()
                .scheduleId("schedule-1").userId("customer-1").fromAccountId("10").toAccountId("99")
                .amount(new BigDecimal("2000.00")).currency("INR").description("Rent")
                .scheduleType(ScheduledTransferType.ONE_TIME).status(ScheduledTransferStatus.ACTIVE)
                .nextRunAt(Instant.parse("2026-07-17T03:30:00Z")).build();

        when(scenarioRepository.findByScenarioIdAndUserId("scenario-1", "customer-1")).thenReturn(Optional.of(scenario));
        when(versionRepository.findByScenarioIdAndScenarioVersion("scenario-1", 1)).thenReturn(Optional.of(version));
        when(resultRepository.findByScenarioIdAndScenarioVersion("scenario-1", 1)).thenReturn(Optional.of(savedResult));
        when(ledgerAccountRepository.findByExternalAccountId("10")).thenReturn(Optional.of(ledgerAccount));
        when(projectionRepository.findById(ledgerId)).thenReturn(Optional.of(projection));
        when(accountServiceClient.getAccountInternal("10")).thenReturn(AccountDto.builder()
                .id(10L).ownerId("customer-1").currency("INR").status("ACTIVE").active(true)
                .accountType("CHECKING").build());
        when(scheduledTransferRepository.findByUserIdAndStatusOrderByNextRunAtAsc("customer-1", ScheduledTransferStatus.ACTIVE))
                .thenReturn(List.of(schedule));
        when(eventRepository.findByUserIdAndDedupeKey(any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(events.get(invocation.getArgument(1))));
        when(eventRepository.save(any(OutcomeDomainEvent.class))).thenAnswer(invocation -> {
            OutcomeDomainEvent event = invocation.getArgument(0);
            events.put(event.getDedupeKey(), event);
            return event;
        });
        OutcomeNotificationDelivery pending = OutcomeNotificationDelivery.builder()
                .deliveryId("delivery-1").warningEventId("warning-1").state("PENDING").attemptCount(0)
                .nextAttemptAt(Instant.now()).dedupeKey("outcome-protection:warning-1").build();
        OutcomeNotificationDelivery delivered = OutcomeNotificationDelivery.builder()
                .deliveryId("delivery-1").warningEventId("warning-1").state("DELIVERED").attemptCount(2)
                .nextAttemptAt(Instant.now()).deliveredAt(Instant.now())
                .dedupeKey("outcome-protection:warning-1").build();
        when(notificationDeliveryService.enqueue(any(), any(), any())).thenReturn(pending, delivered);
        when(notificationDeliveryService.evidence(any())).thenAnswer(invocation -> {
            OutcomeNotificationDelivery delivery = invocation.getArgument(0);
            return new com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.NotificationDeliveryEvidence(
                    delivery.getDeliveryId(), delivery.getState(), delivery.getAttemptCount(), delivery.getNextAttemptAt(),
                    delivery.getDeliveredAt(), delivery.getTerminalAt(), delivery.getSlaEscalatedAt(),
                    delivery.getLastError(), delivery.getDedupeKey());
        });
    }

    @Test
    void preservesWarningEvidenceAndRetriesNotificationWithoutCreatingAnotherWarning() {
        var first = service.refresh("scenario-1", "customer-1");
        var second = service.refresh("scenario-1", "customer-1");

        assertThat(first.freshSourceSnapshot()).isNotNull();
        assertThat(first.freshSourceSnapshot().scheduledCashflows()).singleElement().satisfies(schedule -> {
            assertThat(schedule.scheduleId()).isEqualTo("schedule-1");
            assertThat(schedule.sourceAmount()).isEqualByComparingTo("-2000.00");
            assertThat(schedule.amount()).isEqualByComparingTo("-2000.00");
            assertThat(schedule.date()).isEqualTo(LocalDate.of(2026, 7, 17));
        });
        assertThat(first.protectionAtRisk()).isTrue();
        assertThat(first.notificationEmitted()).isFalse();
        assertThat(first.warningEventId()).isNotBlank();
        assertThat(second.notificationEmitted()).isTrue();
        assertThat(second.warningEventId()).isEqualTo(first.warningEventId());
        assertThat(second.evaluationEventId()).isEqualTo(first.evaluationEventId());
        assertThat(events.values()).extracting(OutcomeDomainEvent::getEventType)
                .containsExactlyInAnyOrder("DIVERGENCE_EVALUATED", "OUTCOME_PROTECTION_AT_RISK");
    }
}
