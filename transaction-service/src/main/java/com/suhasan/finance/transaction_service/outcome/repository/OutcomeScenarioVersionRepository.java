package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenarioVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutcomeScenarioVersionRepository extends JpaRepository<OutcomeScenarioVersion, String> {
    Optional<OutcomeScenarioVersion> findByScenarioIdAndScenarioVersion(String scenarioId, int scenarioVersion);
    Optional<OutcomeScenarioVersion> findByScenarioIdAndMutationIdempotencyKey(String scenarioId, String key);
}
