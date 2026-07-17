package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeGuardrailPolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutcomeGuardrailPolicyRepository extends JpaRepository<OutcomeGuardrailPolicy, String> {
    Optional<OutcomeGuardrailPolicy> findByGuardrailIdAndUserId(String guardrailId, String userId);
    Optional<OutcomeGuardrailPolicy> findByPolicyIdAndUserId(String policyId, String userId);
    Optional<OutcomeGuardrailPolicy> findByUserIdAndConsentIdempotencyKey(String userId, String key);
    List<OutcomeGuardrailPolicy> findAllByOrderByUpdatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OutcomeGuardrailPolicy p where p.guardrailId = :guardrailId and p.userId = :userId")
    Optional<OutcomeGuardrailPolicy> lockByGuardrailAndUser(@Param("guardrailId") String guardrailId,
                                                            @Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OutcomeGuardrailPolicy p where p.policyId = :policyId and p.userId = :userId")
    Optional<OutcomeGuardrailPolicy> lockByPolicyAndUser(@Param("policyId") String policyId,
                                                         @Param("userId") String userId);
}
