package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.outcome.domain.*;
import com.suhasan.finance.transaction_service.outcome.repository.*;
import com.suhasan.finance.transaction_service.outcome.service.OutcomeAuthoritativeSourceService.*;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.OutcomeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutcomeSourceFreshnessServiceTest {
    @Mock OutcomeScenarioRepository scenarios;
    @Mock OutcomeScenarioVersionRepository versions;
    @Mock OutcomeSimulationResultRepository results;
    @Mock OutcomeGuardrailDraftRepository drafts;
    @Mock OutcomeAuthoritativeSourceService sources;
    @Mock OutcomeFreshnessRejectionRecorder recorder;

    private ObjectMapper objectMapper;
    private OutcomeSourceFreshnessService service;
    private OutcomeGuardrailPolicy policy;
    private OutcomeScenarioVersion savedVersion;
    private SourceComponents savedComponents;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new OutcomeSourceFreshnessService(scenarios, versions, results, drafts, sources, recorder, objectMapper);
        OutcomeScenario scenario = OutcomeScenario.builder().scenarioId("scenario-1").userId("customer")
                .name("Protection").currency("USD").timeZone("UTC").currentVersion(2).build();
        savedComponents = components(account("ACTIVE", "customer", "USD", 7L), schedule(3L, "ACTIVE",
                new BigDecimal("25.00"), Instant.parse("2026-08-15T10:00:00Z"), "UTC", "WEEKLY"));
        String componentsJson = objectMapper.writeValueAsString(savedComponents);
        savedVersion = OutcomeScenarioVersion.builder().versionId("version-1").scenarioId("scenario-1")
                .scenarioVersion(1).horizonStart(LocalDate.of(2026, 8, 12)).horizonDays(7)
                .protectedMinimum(new BigDecimal("100.00")).outcomeType(OutcomeType.BALANCE_FLOOR.name())
                .accountIdsJson("[\"account-1\"]").assumptionsJson("[]").shocksJson("[]")
                .sourceFingerprint("saved-fingerprint").sourceFingerprintSchema(OutcomeAuthoritativeSourceService.FINGERPRINT_SCHEMA)
                .sourceComponentsJson(componentsJson).build();
        OutcomeSimulationResult result = OutcomeSimulationResult.builder().resultId("result-1")
                .scenarioId("scenario-1").scenarioVersion(1).certificateHash("aggregate-certificate")
                .replayOutputJson("[{\"certificateHash\":\"certificate-1\"}]").build();
        OutcomeGuardrailDraft draft = OutcomeGuardrailDraft.builder().guardrailId("guardrail-1")
                .scenarioId("scenario-1").resultId("result-1").userId("customer")
                .replayCertificateHash("certificate-1").build();
        policy = OutcomeGuardrailPolicy.builder().policyId("policy-1").guardrailId("guardrail-1")
                .scenarioId("scenario-1").resultId("result-1").userId("customer").build();
        when(scenarios.findByScenarioIdAndUserId("scenario-1", "customer")).thenReturn(Optional.of(scenario));
        when(results.findById("result-1")).thenReturn(Optional.of(result));
        when(versions.findByScenarioIdAndScenarioVersion("scenario-1", 1)).thenReturn(Optional.of(savedVersion));
        when(drafts.findByGuardrailIdAndUserId("guardrail-1", "customer")).thenReturn(Optional.of(draft));
    }

    @Test
    void unchangedCanonicalStatePasses() {
        when(sources.capture(any(), eq("customer"), eq(false))).thenReturn(snapshot("saved-fingerprint", savedComponents));

        assertThatCode(() -> service.assertFresh(policy, "customer", "ACTIVATION", null)).doesNotThrowAnyException();
        verifyNoInteractions(recorder);
    }

    @Test
    void repairCertificateMustBelongToTheImmutableSavedResult() {
        OutcomeGuardrailDraft mismatched = OutcomeGuardrailDraft.builder().guardrailId("guardrail-1")
                .scenarioId("scenario-1").resultId("result-1").userId("customer")
                .replayCertificateHash("certificate-from-another-result").build();
        when(drafts.findByGuardrailIdAndUserId("guardrail-1", "customer")).thenReturn(Optional.of(mismatched));
        when(sources.capture(any(), eq("customer"), eq(false))).thenReturn(snapshot("saved-fingerprint", savedComponents));

        assertThatThrownBy(() -> service.assertFresh(policy, "customer", "ACTIVATION", null))
                .isInstanceOf(ScenarioDivergedException.class);
        ArgumentCaptor<Map<String, ?>> evidence = mapCaptor();
        verify(recorder).record(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), evidence.capture());
        assertThat(evidence.getValue().toString()).contains("REPAIR_CERTIFICATE", "certificateHash")
                .doesNotContain("certificate-from-another-result");
    }

    @Test
    void projectionVersionOnlyChangeFailsClosedWithRedactedImmutableEvidence() {
        SourceComponents changed = components(account("ACTIVE", "customer", "USD", 8L), savedComponents.schedules().get(0));
        when(sources.capture(any(), eq("customer"), eq(false))).thenReturn(snapshot("current-fingerprint", changed));

        assertThatThrownBy(() -> service.assertFresh(policy, "customer", "ACTIVATION", null))
                .isInstanceOf(ScenarioDivergedException.class).hasMessageContaining("SCENARIO_DIVERGED")
                .hasMessageContaining("Refresh or re-run");

        ArgumentCaptor<Map<String, ?>> evidence = mapCaptor();
        verify(recorder).record(eq("customer"), eq("scenario-1"), eq(1), eq("result-1"),
                eq("guardrail-1"), startsWith("scenario-diverged:"), evidence.capture());
        assertThat(evidence.getValue().get("savedSourceFingerprint")).isEqualTo("saved-fingerprint");
        assertThat(evidence.getValue().get("currentSourceFingerprint")).isEqualTo("current-fingerprint");
        assertThat(evidence.getValue().get("moneyMoved")).isEqualTo(false);
        assertThat(evidence.getValue().get("stage")).isEqualTo("ACTIVATION");
        assertThat(evidence.getValue().toString()).contains("projectionVersion").doesNotContain("account-1");
    }

    @Test
    void availableBalanceChangeFailsClosed() {
        AccountComponent saved = savedComponents.accounts().get(0);
        AccountComponent changedAccount = new AccountComponent(saved.accountId(), saved.ledgerAccountId(),
                saved.ledgerOwnerId(), saved.ledgerCurrency(), saved.ledgerKind(), saved.ledgerStatus(),
                saved.ledgerVersion(), new BigDecimal("99.99"), saved.projectionVersion(),
                saved.accountOwnerId(), saved.accountCurrency(), saved.accountStatus(),
                saved.accountActive(), saved.accountType());
        when(sources.capture(any(), eq("customer"), eq(false))).thenReturn(snapshot("changed-balance",
                components(changedAccount, savedComponents.schedules().get(0))));

        assertThatThrownBy(() -> service.assertFresh(policy, "customer", "ACTIVATION", null))
                .isInstanceOf(ScenarioDivergedException.class);
        verify(recorder).record(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void accountStatusOwnershipAndCurrencyChangesFailClosed() {
        SourceComponents changed = components(account("FROZEN", "other-owner", "EUR", 7L), savedComponents.schedules().get(0));
        when(sources.capture(any(), eq("customer"), eq(false))).thenReturn(snapshot("changed-account", changed));

        assertThatThrownBy(() -> service.assertFresh(policy, "customer", "PRE_EXECUTION", null))
                .isInstanceOf(ScenarioDivergedException.class);
        ArgumentCaptor<Map<String, ?>> evidence = mapCaptor();
        verify(recorder).record(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), evidence.capture());
        assertThat(evidence.getValue().toString()).contains("accountStatus", "accountOwnerId", "accountCurrency")
                .doesNotContain("other-owner");
    }

    @Test
    void closedAccountFailsClosed() {
        SourceComponents changed = components(account("CLOSED", "customer", "USD", 7L),
                savedComponents.schedules().get(0));
        when(sources.capture(any(), eq("customer"), eq(false))).thenReturn(snapshot("closed-account", changed));

        assertThatThrownBy(() -> service.assertFresh(policy, "customer", "ACTIVATION", null))
                .isInstanceOf(ScenarioDivergedException.class);
        verify(recorder).record(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void scheduleMembershipAmountTimingRecurrenceStatusOwnerAndVersionChangesFailClosed() {
        ScheduleComponent changedSchedule = new ScheduleComponent("schedule-1", "other-owner", "account-1", "account-2",
                new BigDecimal("30.00"), "USD", "RECURRING", "MONTHLY",
                Instant.parse("2026-08-16T11:00:00Z"), null, "Asia/Kolkata",
                LocalDateTime.of(2026, 8, 16, 16, 30), "LATER", "REJECT", 16,
                false, "PAUSED", "OPTIONAL", 4L);
        when(sources.capture(any(), eq("customer"), eq(false)))
                .thenReturn(snapshot("changed-schedule", components(savedComponents.accounts().get(0), changedSchedule)));

        assertThatThrownBy(() -> service.assertFresh(policy, "customer", "PRE_EXECUTION", null))
                .isInstanceOf(ScenarioDivergedException.class);
        ArgumentCaptor<Map<String, ?>> evidence = mapCaptor();
        verify(recorder).record(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), evidence.capture());
        assertThat(evidence.getValue().toString()).contains("amount", "nextRunAt", "frequency", "sourceTimeZone",
                "status", "ownerId", "version").doesNotContain("other-owner");
    }

    @Test
    void scheduleAdditionOrRemovalFailsClosed() {
        SourceComponents removed = new SourceComponents(OutcomeAuthoritativeSourceService.FINGERPRINT_SCHEMA,
                savedComponents.accounts(), List.of(), savedComponents.protectedObligation());
        when(sources.capture(any(), eq("customer"), eq(false))).thenReturn(snapshot("schedule-removed", removed));

        assertThatThrownBy(() -> service.assertFresh(policy, "customer", "PRE_EXECUTION", null))
                .isInstanceOf(ScenarioDivergedException.class);
        ArgumentCaptor<Map<String, ?>> evidence = mapCaptor();
        verify(recorder).record(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), evidence.capture());
        assertThat(evidence.getValue().toString()).contains("REMOVED", "membership");
    }

    @Test
    void scheduleAdditionFailsClosed() {
        ScheduleComponent added = new ScheduleComponent("schedule-2", "customer", "account-1", "account-2",
                new BigDecimal("10.00"), "USD", "ONE_TIME", "ONE_TIME",
                Instant.parse("2026-08-17T10:00:00Z"), null, "UTC",
                LocalDateTime.of(2026, 8, 17, 10, 0), "EARLIER", "SHIFT_FORWARD", 17,
                false, "ACTIVE", "OPTIONAL", 1L);
        SourceComponents current = new SourceComponents(OutcomeAuthoritativeSourceService.FINGERPRINT_SCHEMA,
                savedComponents.accounts(), List.of(savedComponents.schedules().get(0), added),
                savedComponents.protectedObligation());
        when(sources.capture(any(), eq("customer"), eq(false))).thenReturn(snapshot("schedule-added", current));

        assertThatThrownBy(() -> service.assertFresh(policy, "customer", "ACTIVATION", null))
                .isInstanceOf(ScenarioDivergedException.class);
        ArgumentCaptor<Map<String, ?>> evidence = mapCaptor();
        verify(recorder).record(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), evidence.capture());
        assertThat(evidence.getValue().toString()).contains("ADDED", "membership").doesNotContain("schedule-2");
    }

    @Test
    void protectedObligationChangeFailsClosed() {
        ProtectedObligationComponent changed = new ProtectedObligationComponent("protected-schedule", 10L,
                "CANCELLED", "customer", "account-1", "account-2", true, false,
                new BigDecimal("80.00"), "USD", "ONE_TIME", "ONE_TIME",
                Instant.parse("2026-08-14T10:00:00Z"), LocalDate.of(2026, 8, 14), "UTC", "UTC",
                null, 7L, false);
        SourceComponents current = new SourceComponents(OutcomeAuthoritativeSourceService.FINGERPRINT_SCHEMA, savedComponents.accounts(),
                savedComponents.schedules(), changed);
        when(sources.capture(any(), eq("customer"), eq(false))).thenReturn(snapshot("changed-obligation", current));

        assertThatThrownBy(() -> service.assertFresh(policy, "customer", "PRE_AUTHORIZATION_COMPLETION", "execution-1"))
                .isInstanceOf(ScenarioDivergedException.class);
        verify(recorder).record(anyString(), anyString(), anyInt(), anyString(), anyString(),
                contains("execution-1"), anyMap());
    }

    @Test
    void legacyFingerprintSchemaRequiresNewScenarioAndCertificate() {
        org.springframework.test.util.ReflectionTestUtils.setField(savedVersion, "sourceFingerprintSchema", "outcome-source-v1");
        when(sources.capture(any(), eq("customer"), eq(false))).thenReturn(snapshot("saved-fingerprint", savedComponents));

        assertThatThrownBy(() -> service.assertFresh(policy, "customer", "CONSENT", null))
                .isInstanceOf(ScenarioDivergedException.class);
    }

    private Snapshot snapshot(String fingerprint, SourceComponents components) {
        return new Snapshot(List.of(), List.of(), null, fingerprint, components);
    }

    private SourceComponents components(AccountComponent account, ScheduleComponent schedule) {
        ProtectedObligationComponent obligation = new ProtectedObligationComponent("protected-schedule", 9L,
                "ACTIVE", "customer", "account-1", "account-2", true, false,
                new BigDecimal("75.00"), "USD", "ONE_TIME", "ONE_TIME",
                Instant.parse("2026-08-14T10:00:00Z"), LocalDate.of(2026, 8, 14), "UTC", "UTC",
                null, 7L, true);
        return new SourceComponents(OutcomeAuthoritativeSourceService.FINGERPRINT_SCHEMA, List.of(account), List.of(schedule), obligation);
    }

    private AccountComponent account(String status, String owner, String currency, long projectionVersion) {
        return new AccountComponent("account-1", "ledger-1", "customer", "USD", "CUSTOMER", "ACTIVE",
                2L, new BigDecimal("100.00"), projectionVersion, owner, currency, status, true, "CHECKING");
    }

    private ScheduleComponent schedule(long version, String status, BigDecimal amount, Instant nextRun,
                                       String timeZone, String frequency) {
        return new ScheduleComponent("schedule-1", "customer", "account-1", "account-2", amount, "USD",
                "RECURRING", frequency, nextRun, null, timeZone,
                LocalDateTime.of(2026, 8, 15, 10, 0), "EARLIER", "SHIFT_FORWARD", 15,
                false, status, "OPTIONAL", version);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, ?>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
