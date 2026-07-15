package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeDomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutcomeDomainEventRepository extends JpaRepository<OutcomeDomainEvent, String> {
    boolean existsByUserIdAndEventType(String userId, String eventType);
    Optional<OutcomeDomainEvent> findByEventIdAndUserId(String eventId, String userId);
    Optional<OutcomeDomainEvent> findByUserIdAndDedupeKey(String userId, String dedupeKey);
}
