package com.suhasan.finance.transaction_service.outcome.service;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenario;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutcomeScenarioMonitorTest {
    @Mock OutcomeScenarioRepository scenarioRepository;
    @Mock OutcomeProtectionService protectionService;
    @Mock PlatformTransactionManager transactionManager;

    private OutcomeScenarioMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new OutcomeScenarioMonitor(scenarioRepository, protectionService,
                new TransactionTemplate(transactionManager));
    }

    private static OutcomeScenario scenario(String id) {
        return OutcomeScenario.builder().scenarioId(id).userId("customer").name(id).status("ACTIVE")
                .currentVersion(1).currency("USD").timeZone("UTC").build();
    }

    private Optional<OutcomeScenario> claim() {
        return scenarioRepository.claimNextActiveForMonitoring(any(LocalDateTime.class), anyCollection());
    }

    @Test
    void refreshesEachClaimedScenarioOnceInItsOwnTransactionThenStops() {
        OutcomeScenario first = scenario("s-1");
        OutcomeScenario second = scenario("s-2");
        when(claim()).thenReturn(Optional.of(first), Optional.of(second), Optional.empty());

        monitor.monitorActiveScenarios();

        verify(protectionService).refreshClaimed(first);
        verify(protectionService).refreshClaimed(second);
        // one transaction per scenario, plus the final empty claim
        verify(transactionManager, times(3)).commit(any());
    }

    @Test
    void neverAsksForAScenarioItAlreadyHandledInTheSameRun() {
        when(claim()).thenReturn(Optional.of(scenario("s-1")), Optional.of(scenario("s-2")), Optional.empty());

        monitor.monitorActiveScenarios();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> excluded = ArgumentCaptor.forClass(Collection.class);
        verify(scenarioRepository, times(3)).claimNextActiveForMonitoring(any(LocalDateTime.class), excluded.capture());
        List<Collection<String>> calls = excluded.getAllValues();
        assertThat(calls.get(0)).as("NOT IN () is invalid SQL, so a placeholder is always present")
                .isNotEmpty().doesNotContain("s-1", "s-2");
        assertThat(calls.get(1)).contains("s-1").doesNotContain("s-2");
        assertThat(calls.get(2)).contains("s-1", "s-2");
    }

    @Test
    void onlyClaimsScenariosNotCheckedSinceTheRunBegan() {
        when(claim()).thenReturn(Optional.of(scenario("s-1")), Optional.of(scenario("s-2")), Optional.empty());
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);

        monitor.monitorActiveScenarios();

        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(scenarioRepository, times(3)).claimNextActiveForMonitoring(cutoff.capture(), anyCollection());
        assertThat(cutoff.getAllValues()).as("one cutoff for the whole run, taken when it began")
                .hasSize(3).containsOnly(cutoff.getAllValues().get(0));
        assertThat(cutoff.getValue()).isBetween(before, after);
    }

    @Test
    void aFailingScenarioRollsBackAloneAndTheRestStillRun() {
        OutcomeScenario broken = scenario("s-broken");
        OutcomeScenario healthy = scenario("s-ok");
        when(claim()).thenReturn(Optional.of(broken), Optional.of(healthy), Optional.empty());
        doThrow(new IllegalStateException("account service unavailable"))
                .when(protectionService).refreshClaimed(broken);

        monitor.monitorActiveScenarios();

        verify(protectionService).refreshClaimed(healthy);
        verify(transactionManager).rollback(any());
        verify(transactionManager, times(2)).commit(any());
    }

    @Test
    void aFailingScenarioIsNotRetriedWithinTheSameRun() {
        OutcomeScenario broken = scenario("s-broken");
        // A repository that would keep returning the broken scenario if it were not excluded.
        when(claim()).thenAnswer(invocation -> {
            Collection<?> excluded = invocation.getArgument(1);
            return excluded.contains("s-broken") ? Optional.empty() : Optional.of(broken);
        });
        doThrow(new IllegalStateException("boom")).when(protectionService).refreshClaimed(broken);

        monitor.monitorActiveScenarios();

        verify(protectionService, times(1)).refreshClaimed(broken);
    }

    @Test
    void stopsTheRunWhenTheClaimItselfFails() {
        when(claim()).thenThrow(new IllegalStateException("database unavailable"));

        monitor.monitorActiveScenarios();

        verify(scenarioRepository, times(1)).claimNextActiveForMonitoring(any(LocalDateTime.class), anyCollection());
        verify(protectionService, never()).refreshClaimed(any());
    }

    @Test
    void handlesAtMostTheConfiguredNumberOfScenariosPerRun() {
        when(claim()).thenAnswer(invocation -> {
            Collection<?> excluded = invocation.getArgument(1);
            return Optional.of(scenario("s-" + excluded.size()));
        });

        monitor.monitorActiveScenarios();

        verify(protectionService, times(OutcomeScenarioMonitor.MAX_SCENARIOS_PER_RUN)).refreshClaimed(any());
    }
}
