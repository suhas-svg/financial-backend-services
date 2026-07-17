package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeGuardrailExecution;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutcomeGuardrailExecutionRepository extends JpaRepository<OutcomeGuardrailExecution, String> {
    Optional<OutcomeGuardrailExecution> findByUserIdAndIdempotencyKey(String userId, String key);
    Optional<OutcomeGuardrailExecution> findByExecutionIdAndUserId(String executionId, String userId);
    List<OutcomeGuardrailExecution> findByPolicyIdAndStatus(String policyId, String status);
    List<OutcomeGuardrailExecution> findByPolicyIdOrderByCreatedAtDesc(String policyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutcomeGuardrailExecution e where e.executionId = :executionId and e.userId = :userId")
    Optional<OutcomeGuardrailExecution> lockByExecutionAndUser(@Param("executionId") String executionId,
                                                               @Param("userId") String userId);
}
