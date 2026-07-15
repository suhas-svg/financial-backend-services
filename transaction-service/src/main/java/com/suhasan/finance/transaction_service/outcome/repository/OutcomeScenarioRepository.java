package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutcomeScenarioRepository extends JpaRepository<OutcomeScenario, String> {
    Optional<OutcomeScenario> findByScenarioIdAndUserId(String scenarioId, String userId);
    Optional<OutcomeScenario> findByUserIdAndCreateIdempotencyKey(String userId, String createIdempotencyKey);
    List<OutcomeScenario> findByUserIdOrderByUpdatedAtDesc(String userId);
    List<OutcomeScenario> findTop100ByStatusOrderByLastCheckedAtAsc(String status);
}
