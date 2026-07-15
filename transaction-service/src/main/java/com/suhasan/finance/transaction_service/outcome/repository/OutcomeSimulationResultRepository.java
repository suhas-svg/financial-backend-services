package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeSimulationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutcomeSimulationResultRepository extends JpaRepository<OutcomeSimulationResult, String> {
    Optional<OutcomeSimulationResult> findByScenarioIdAndScenarioVersion(String scenarioId, int scenarioVersion);
}
