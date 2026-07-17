package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeGuardrailControlEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutcomeGuardrailControlEventRepository extends JpaRepository<OutcomeGuardrailControlEvent, String> {
    Optional<OutcomeGuardrailControlEvent> findByActorAndIdempotencyKey(String actor, String key);
    List<OutcomeGuardrailControlEvent> findTop100ByOrderByCreatedAtDesc();
}
