package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeDomainEvent;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeGuardrailDraft;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenario;
import com.suhasan.finance.transaction_service.outcome.fx.OutcomeFxConverter;
import com.suhasan.finance.transaction_service.outcome.repository.*;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.RepairDraftSelectRequest;
import com.suhasan.finance.transaction_service.repository.ScheduledTransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutcomeRepairDraftSelectionTest {
    @Mock OutcomeScenarioRepository scenarioRepository;
    @Mock OutcomeScenarioVersionRepository versionRepository;
    @Mock OutcomeSimulationResultRepository resultRepository;
    @Mock OutcomeGuardrailDraftRepository guardrailRepository;
    @Mock OutcomeDomainEventRepository eventRepository;
    @Mock LedgerAccountRepository ledgerAccountRepository;
    @Mock LedgerBalanceProjectionRepository projectionRepository;
    @Mock ScheduledTransferRepository scheduledTransferRepository;
    @Mock OutcomeSimulationEngine simulationEngine;
    @Mock OutcomeScheduledTransferForecaster scheduledTransferForecaster;
    @Mock OutcomeFxConverter fxConverter;
    @Mock OutcomeNotificationDeliveryService notificationDeliveryService;
    @Mock OutcomeGuardrailService guardrailService;
    @Spy ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @InjectMocks OutcomeProtectionService service;

    @Test
    void selectionIsIdempotentAndDoesNotInvokeFinancialMutationDependencies() {
        OutcomeGuardrailDraft draft = OutcomeGuardrailDraft.builder()
                .guardrailId("guardrail-1")
                .scenarioId("scenario-1")
                .resultId("result-1")
                .userId("customer")
                .guardrailType("SHIFT_OPTIONAL_SCHEDULE")
                .thresholdAmount(new BigDecimal("100.00"))
                .currency("USD")
                .scopeJson("[\"101\"]")
                .previewText("Preview only")
                .expiresAt(Instant.now().plusSeconds(3600))
                .status("DRAFT")
                .alternativeRank(1)
                .candidateActionsJson("[]")
                .rejectionReasonsJson("[]")
                .replayCertificateHash("a".repeat(64))
                .build();
        OutcomeScenario scenario = OutcomeScenario.builder()
                .scenarioId("scenario-1")
                .userId("customer")
                .currentVersion(1)
                .currency("USD")
                .timeZone("UTC")
                .build();
        when(guardrailRepository.findByGuardrailIdAndUserId("guardrail-1", "customer"))
                .thenReturn(Optional.of(draft));
        when(guardrailRepository.findByUserIdAndPreviewSelectionIdempotencyKey(
                "customer", "repair-select-test")).thenReturn(Optional.empty());
        when(scenarioRepository.findById("scenario-1")).thenReturn(Optional.of(scenario));
        when(eventRepository.findByUserIdAndDedupeKey(any(), any())).thenReturn(Optional.empty());
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, OutcomeDomainEvent.class));

        var first = service.selectRepairDraft("guardrail-1", new RepairDraftSelectRequest(true),
                "customer", "repair-select-test");
        var replay = service.selectRepairDraft("guardrail-1", new RepairDraftSelectRequest(true),
                "customer", "repair-select-test");

        assertThat(first.previewSelectedAt()).isNotNull();
        assertThat(replay.previewSelectedAt()).isEqualTo(first.previewSelectedAt());
        assertThat(first.replayCertificateHash()).isEqualTo("a".repeat(64));
        verify(guardrailRepository, times(1)).save(draft);
        verifyNoInteractions(scheduledTransferRepository, ledgerAccountRepository,
                projectionRepository, fxConverter, simulationEngine);
    }
}
