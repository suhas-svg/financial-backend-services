package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OutcomeScenarioRepository extends JpaRepository<OutcomeScenario, String> {
    Optional<OutcomeScenario> findByScenarioIdAndUserId(String scenarioId, String userId);
    Optional<OutcomeScenario> findByUserIdAndCreateIdempotencyKey(String userId, String createIdempotencyKey);
    List<OutcomeScenario> findByUserIdOrderByUpdatedAtDesc(String userId);

    /**
     * Locks the least recently checked ACTIVE scenario that no other replica's monitor currently holds,
     * that was last checked before {@code checkedBefore} (so a scenario another replica already refreshed
     * during this cycle is not evaluated twice), whose failure backoff has elapsed (so scenarios that
     * keep failing cannot crowd healthy ones out of a run), and that is not in {@code excludedIds}. Callers pass a
     * non-empty exclusion list (a placeholder when nothing has been visited yet) because
     * {@code NOT IN ()} is not valid SQL. {@code checkedBefore} is a UTC wall-clock value, matching how
     * {@code last_checked_at} is stored.
     */
    @Query(value = """
            select * from outcome_scenarios
            where status = 'ACTIVE'
              and (last_checked_at is null or last_checked_at < :checkedBefore)
              and (monitor_next_attempt_at is null or monitor_next_attempt_at <= :checkedBefore)
              and scenario_id not in (:excludedIds)
            order by last_checked_at asc nulls first
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<OutcomeScenario> claimNextActiveForMonitoring(
            @Param("checkedBefore") LocalDateTime checkedBefore,
            @Param("excludedIds") Collection<String> excludedIds);
}
