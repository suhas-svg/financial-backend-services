package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.StepUpClientDtos;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import com.suhasan.finance.transaction_service.outcome.domain.*;
import com.suhasan.finance.transaction_service.outcome.repository.*;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import com.suhasan.finance.transaction_service.service.TransferAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutcomeGuardrailServiceTest {
    @Mock OutcomeGuardrailDraftRepository drafts;
    @Mock OutcomeGuardrailPolicyRepository policies;
    @Mock OutcomeGuardrailExecutionRepository executions;
    @Mock OutcomeGuardrailRuntimeControlRepository controls;
    @Mock OutcomeScenarioRepository scenarios;
    @Mock OutcomeScenarioVersionRepository scenarioVersions;
    @Mock OutcomeDomainEventRepository events;
    @Mock LedgerAccountRepository accounts;
    @Mock LedgerBalanceProjectionRepository projections;
    @Mock ResilientAccountServiceClient accountClient;
    @Mock TransferAuthorizationService transfers;
    @Mock OutcomeNotificationDeliveryService notifications;

    private OutcomeGuardrailService service;
    private OutcomeGuardrailPolicy policy;

    @BeforeEach
    void setUp() {
        service = new OutcomeGuardrailService(drafts, policies, executions, controls, scenarios, scenarioVersions, events,
                accounts, projections, accountClient, transfers, notifications,
                new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(service, "termsVersion", "2026-07-16.1");
        String termsHash = service.terms().hash();
        policy = OutcomeGuardrailPolicy.builder()
                .policyId("policy-1").guardrailId("guardrail-1").scenarioId("scenario-1")
                .resultId("result-1").userId("customer").fundingAccountId("funding")
                .protectedAccountId("protected").currency("USD")
                .triggerThreshold(new BigDecimal("100.00")).maxActionAmount(new BigDecimal("50.00"))
                .totalLimit(new BigDecimal("100.00")).totalExecuted(BigDecimal.ZERO.setScale(2))
                .totalReserved(BigDecimal.ZERO.setScale(2)).maxExecutions(3).executionCount(0)
                .termsVersion("2026-07-16.1").termsHash(termsHash).consentEvidenceJson("{}")
                .consentIdempotencyKey("consent-key").consentRequestFingerprint("fingerprint")
                .activationChallengeId("challenge-1").activationFingerprint("activation-fingerprint")
                .activationChallengeExpiresAt(Instant.now().plusSeconds(300)).status("ACTIVE")
                .expiresAt(Instant.now().plusSeconds(3600)).consentedAt(Instant.now()).build();
    }

    @Test
    void activationConsumesActionBoundMfaAndDoesNotSubmitTransfer() {
        policy.setStatus("CONSENT_PENDING");
        when(policies.lockByGuardrailAndUser("guardrail-1", "customer")).thenReturn(Optional.of(policy));
        stubEvidenceDependencies();

        GuardrailPolicyResponse result = service.activate("guardrail-1", new GuardrailActivationRequest("123456"), "customer");

        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(accountClient).consumeStepUpChallenge(eq("challenge-1"), argThat(request ->
                request.userId().equals("customer") && request.actionFingerprint().equals("activation-fingerprint")
                        && request.consumerKey().equals("policy-1") && request.proof().equals("123456")));
        verifyNoInteractions(transfers);
    }

    @Test
    void consentUsesProtectedMinimumAsExecutionTriggerNotRepairAmount() {
        Instant expiry = Instant.now().plusSeconds(1800);
        OutcomeGuardrailDraft draft = OutcomeGuardrailDraft.builder()
                .guardrailId("guardrail-1").scenarioId("scenario-1").resultId("result-1")
                .userId("customer").guardrailType("RESERVE_BUFFER")
                .thresholdAmount(new BigDecimal("49.00")).currency("USD")
                .scopeJson("[\"protected\"]").previewText("Preview").expiresAt(expiry).status("DRAFT").build();
        OutcomeScenario scenario = OutcomeScenario.builder()
                .scenarioId("scenario-1").userId("customer").currentVersion(1).currency("USD").build();
        OutcomeScenarioVersion scenarioVersion = OutcomeScenarioVersion.builder()
                .versionId("version-1").scenarioId("scenario-1").scenarioVersion(1)
                .protectedMinimum(new BigDecimal("200.00")).build();
        when(drafts.lockByGuardrailAndUser("guardrail-1", "customer")).thenReturn(Optional.of(draft));
        when(scenarios.findByScenarioIdAndUserId("scenario-1", "customer")).thenReturn(Optional.of(scenario));
        when(scenarioVersions.findByScenarioIdAndScenarioVersion("scenario-1", 1))
                .thenReturn(Optional.of(scenarioVersion));
        when(policies.findByUserIdAndConsentIdempotencyKey("customer", "consent-key"))
                .thenReturn(Optional.empty());
        when(policies.findByGuardrailIdAndUserId("guardrail-1", "customer")).thenReturn(Optional.empty());
        when(accounts.findByExternalAccountId("funding"))
                .thenReturn(Optional.of(account("funding", new BigDecimal("2500.00"))));
        when(accounts.findByExternalAccountId("protected"))
                .thenReturn(Optional.of(account("protected", new BigDecimal("151.00"))));
        when(accountClient.createStepUpChallenge(any())).thenReturn(
                new StepUpClientDtos.CreateChallengeResponse("challenge-2", expiry));
        when(policies.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubEvidenceDependencies();

        GuardrailConsentRequest request = new GuardrailConsentRequest(true, "2026-07-16.1",
                service.terms().hash(), "funding", "protected", new BigDecimal("49.00"),
                new BigDecimal("49.00"), 1, expiry);

        GuardrailPolicyResponse result = service.consent("guardrail-1", request, "customer", "consent-key");

        assertThat(result.triggerThreshold()).isEqualByComparingTo("200.00");
        assertThat(result.maxActionAmount()).isEqualByComparingTo("49.00");
    }

    @Test
    void killSwitchFailsClosedBeforeAnyTransfer() {
        when(policies.lockByGuardrailAndUser("guardrail-1", "customer")).thenReturn(Optional.of(policy));
        when(executions.findByUserIdAndIdempotencyKey("customer", "action-key")).thenReturn(Optional.empty());
        when(controls.findById(OutcomeGuardrailControlService.GLOBAL_CONTROL_ID)).thenReturn(Optional.of(control(false)));

        assertThatThrownBy(() -> service.execute("guardrail-1",
                new GuardrailExecutionRequest(true, new BigDecimal("25.00")), "customer", "action-key"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("kill switch");
        verifyNoInteractions(transfers);
    }

    @Test
    void replayReturnsSameExecutionWithoutMovingMoneyAgain() {
        OutcomeGuardrailExecution prior = execution("execution-1", "COMPLETED", new BigDecimal("25.00"));
        when(executions.findByUserIdAndIdempotencyKey("customer", "action-key")).thenReturn(Optional.of(prior));

        GuardrailExecutionResponse replay = service.execute("guardrail-1",
                new GuardrailExecutionRequest(true, new BigDecimal("25.00")), "customer", "action-key");

        assertThat(replay.executionId()).isEqualTo("execution-1");
        assertThat(replay.status()).isEqualTo("COMPLETED");
        verifyNoInteractions(transfers);
    }

    @Test
    void completedTransferRemainsCompletedWhenNotificationEvidenceFails() {
        executableState();
        when(transfers.submit(any(), eq("customer"), startsWith("guardrail:"))).thenReturn(TransactionResponse.builder()
                .transactionId("transaction-1").status(TransactionStatus.COMPLETED)
                .authorizationRequired(false).build());
        when(executions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubEvent();
        when(notifications.enqueueEvent(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("notification boundary unavailable"));

        GuardrailExecutionResponse result = service.execute("guardrail-1",
                new GuardrailExecutionRequest(true, new BigDecimal("25.00")), "customer", "action-key");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.transactionId()).isEqualTo("transaction-1");
        assertThat(result.lastError()).contains("state preserved").contains("notification boundary unavailable");
        assertThat(policy.getTotalExecuted()).isEqualByComparingTo("25.00");
        assertThat(policy.getExecutionCount()).isEqualTo(1);
    }

    @Test
    void revocationCancelsPendingAuthorizationAndReleasesReservation() {
        policy.setTotalReserved(new BigDecimal("25.00"));
        OutcomeGuardrailExecution pending = execution("execution-1", "AWAITING_AUTHORIZATION", new BigDecimal("25.00"));
        pending.setTransferAuthorizationId("authorization-1");
        when(policies.lockByGuardrailAndUser("guardrail-1", "customer")).thenReturn(Optional.of(policy));
        when(executions.findByPolicyIdAndStatus("policy-1", "AWAITING_AUTHORIZATION")).thenReturn(List.of(pending));
        when(executions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubEvidenceDependencies();

        GuardrailPolicyResponse result = service.revoke("guardrail-1",
                new GuardrailLifecycleRequest("Customer disabled protection"), "customer", "revoke-key");

        assertThat(result.status()).isEqualTo("REVOKED");
        assertThat(pending.getStatus()).isEqualTo("CANCELLED");
        assertThat(policy.getTotalReserved()).isEqualByComparingTo("0.00");
        verify(transfers).cancel("authorization-1", "customer");
    }

    private void executableState() {
        when(executions.findByUserIdAndIdempotencyKey("customer", "action-key")).thenReturn(Optional.empty());
        when(policies.lockByGuardrailAndUser("guardrail-1", "customer")).thenReturn(Optional.of(policy));
        when(controls.findById(OutcomeGuardrailControlService.GLOBAL_CONTROL_ID)).thenReturn(Optional.of(control(true)));
        when(executions.findByPolicyIdAndStatus("policy-1", "AWAITING_AUTHORIZATION")).thenReturn(List.of());
        LedgerAccount funding = account("funding", new BigDecimal("200.00"));
        LedgerAccount protectedAccount = account("protected", new BigDecimal("50.00"));
        when(accounts.findByExternalAccountId("funding")).thenReturn(Optional.of(funding));
        when(accounts.findByExternalAccountId("protected")).thenReturn(Optional.of(protectedAccount));
        when(projections.findById(funding.getLedgerAccountId())).thenReturn(Optional.of(LedgerBalanceProjection.open(funding.getLedgerAccountId(), new BigDecimal("200.00"))));
        when(projections.findById(protectedAccount.getLedgerAccountId())).thenReturn(Optional.of(LedgerBalanceProjection.open(protectedAccount.getLedgerAccountId(), new BigDecimal("50.00"))));
    }

    private LedgerAccount account(String externalId, BigDecimal ignoredBalance) {
        return LedgerAccount.builder().ledgerAccountId(UUID.randomUUID()).externalAccountId(externalId)
                .ownerId("customer").accountKind(LedgerAccountKind.CUSTOMER).currency("USD")
                .status(LedgerAccountStatus.ACTIVE).build();
    }

    private OutcomeGuardrailRuntimeControl control(boolean enabled) {
        return OutcomeGuardrailRuntimeControl.builder().controlId(OutcomeGuardrailControlService.GLOBAL_CONTROL_ID)
                .executionEnabled(enabled).reason(enabled ? "Approved window" : "Emergency stop")
                .changedBy("operator").updatedAt(Instant.now()).build();
    }

    private OutcomeGuardrailExecution execution(String id, String status, BigDecimal amount) {
        return OutcomeGuardrailExecution.builder().executionId(id).policyId("policy-1").guardrailId("guardrail-1")
                .userId("customer").amount(amount).currency("USD").idempotencyKey("action-key")
                .requestFingerprint(fingerprintFor(amount)).transferIdempotencyKey("guardrail:" + id)
                .status(status).createdAt(Instant.now()).build();
    }

    private String fingerprintFor(BigDecimal amount) {
        OutcomeGuardrailExecution probe = null;
        try {
            var method = OutcomeGuardrailService.class.getDeclaredMethod("fingerprint", Object.class);
            method.setAccessible(true);
            return (String) method.invoke(service, java.util.Map.of(
                    "guardrailId", "guardrail-1", "amount", amount.setScale(2), "confirmed", true));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private void stubEvidenceDependencies() {
        when(controls.findById(OutcomeGuardrailControlService.GLOBAL_CONTROL_ID)).thenReturn(Optional.of(control(true)));
        stubEvent();
        OutcomeNotificationDelivery delivery = OutcomeNotificationDelivery.builder()
                .deliveryId("delivery-1").state("PENDING").attemptCount(0).dedupeKey("dedupe")
                .nextAttemptAt(Instant.now()).build();
        when(notifications.enqueueEvent(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(delivery);
        when(notifications.evidence(delivery)).thenReturn(new NotificationDeliveryEvidence(
                "delivery-1", "PENDING", 0, delivery.getNextAttemptAt(), null, null, null, null, "dedupe"));
    }

    private void stubEvent() {
        when(events.findByUserIdAndDedupeKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(scenarios.findById("scenario-1")).thenReturn(Optional.of(OutcomeScenario.builder()
                .scenarioId("scenario-1").userId("customer").currentVersion(1).currency("USD").build()));
    }
}
