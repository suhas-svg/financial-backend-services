package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeGuardrailDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutcomeGuardrailDraftRepository extends JpaRepository<OutcomeGuardrailDraft, String> {
    List<OutcomeGuardrailDraft> findByResultIdOrderByCreatedAtAsc(String resultId);
    Optional<OutcomeGuardrailDraft> findByGuardrailIdAndUserId(String guardrailId, String userId);
    Optional<OutcomeGuardrailDraft> findByUserIdAndAcceptanceIdempotencyKey(String userId, String key);
}
