package com.suhasan.finance.transaction_service.outcome.service;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenario;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Periodically re-evaluates ACTIVE Outcome Protection scenarios against fresh ledger and schedule state.
 *
 * <p>Every replica runs this job. Each scenario is claimed with {@code FOR UPDATE SKIP LOCKED} and
 * processed in its own short transaction, so replicas split the work instead of evaluating the same
 * scenario at once (which collided on the scenario's {@code @Version} and rolled back the whole batch).
 * A scenario that fails rolls back alone, is not retried again in the same run, and is backed off
 * exponentially by {@link OutcomeMonitorHealth} so repeated failures cannot crowd out healthy scenarios.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutcomeScenarioMonitor {

    /** Upper bound on scenarios one run of one replica re-evaluates (unchanged from before). */
    static final int MAX_SCENARIOS_PER_RUN = 100;

    /** Scenario ids are UUIDs, so this can never match one; it keeps {@code NOT IN (...)} non-empty. */
    private static final String NOTHING_VISITED = "-";

    private final OutcomeScenarioRepository scenarioRepository;
    private final OutcomeProtectionService protectionService;
    private final TransactionTemplate transactionTemplate;
    private final OutcomeMonitorHealth monitorHealth;

    @Scheduled(fixedDelayString = "${outcome-protection.monitor.fixed-delay-ms:300000}",
            initialDelayString = "${outcome-protection.monitor.initial-delay-ms:60000}")
    public void monitorActiveScenarios() {
        // A scenario checked after this run began (by this or another replica) is done for this cycle.
        // Row locks alone do not give that: once one replica commits, another could claim the same row.
        LocalDateTime checkedBefore = LocalDateTime.now(ZoneOffset.UTC);
        Set<String> visited = new LinkedHashSet<>();
        visited.add(NOTHING_VISITED);
        for (int handled = 0; handled < MAX_SCENARIOS_PER_RUN; handled++) {
            if (!monitorNext(checkedBefore, visited)) {
                return;
            }
        }
    }

    /** Claims and refreshes one scenario; returns false when the run should stop. */
    private boolean monitorNext(LocalDateTime checkedBefore, Set<String> visited) {
        AtomicReference<String> claimedId = new AtomicReference<>();
        try {
            Boolean found = transactionTemplate.execute(status -> {
                Optional<OutcomeScenario> next = scenarioRepository
                        .claimNextActiveForMonitoring(checkedBefore, new ArrayList<>(visited));
                if (next.isEmpty()) {
                    return false;
                }
                OutcomeScenario scenario = next.get();
                claimedId.set(scenario.getScenarioId());
                protectionService.refreshClaimed(scenario);
                return true;
            });
            if (!Boolean.TRUE.equals(found)) {
                return false;
            }
            visited.add(claimedId.get());
            return true;
        } catch (RuntimeException failure) {
            String scenarioId = claimedId.get();
            if (scenarioId == null) {
                log.warn("Outcome Protection monitor could not claim a scenario: {}", failure.getMessage());
                return false;
            }
            visited.add(scenarioId);
            log.warn("Outcome Protection monitor failed for scenario {}: {}", scenarioId, failure.getMessage());
            try {
                monitorHealth.recordFailure(scenarioId, failure);
            } catch (RuntimeException recordFailure) {
                // Without the record the scenario simply retries next cycle, as it did before backoff existed.
                log.warn("Could not record monitor failure for scenario {}: {}", scenarioId, recordFailure.getMessage());
            }
            return true;
        }
    }
}
