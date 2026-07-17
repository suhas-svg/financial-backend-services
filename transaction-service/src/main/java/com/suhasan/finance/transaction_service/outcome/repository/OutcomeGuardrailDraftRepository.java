package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeGuardrailDraft;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutcomeGuardrailDraftRepository extends JpaRepository<OutcomeGuardrailDraft, String> {
    List<OutcomeGuardrailDraft> findByResultIdOrderByCreatedAtAsc(String resultId);
    Optional<OutcomeGuardrailDraft> findByGuardrailIdAndUserId(String guardrailId, String userId);
    Optional<OutcomeGuardrailDraft> findByUserIdAndAcceptanceIdempotencyKey(String userId, String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from OutcomeGuardrailDraft d where d.guardrailId = :guardrailId and d.userId = :userId")
    Optional<OutcomeGuardrailDraft> lockByGuardrailAndUser(@Param("guardrailId") String guardrailId,
                                                           @Param("userId") String userId);
}
