package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeNotificationDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutcomeNotificationDeliveryRepository extends JpaRepository<OutcomeNotificationDelivery, String> {
    Optional<OutcomeNotificationDelivery> findByWarningEventId(String warningEventId);
    Optional<OutcomeNotificationDelivery> findTopByUserIdAndScenarioIdOrderByCreatedAtDesc(String userId, String scenarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from OutcomeNotificationDelivery d where d.deliveryId = :deliveryId")
    Optional<OutcomeNotificationDelivery> lockById(@Param("deliveryId") String deliveryId);

    @Query("select d from OutcomeNotificationDelivery d where d.state in ('PENDING','RETRY_SCHEDULED') and d.nextAttemptAt <= :now order by d.createdAt")
    List<OutcomeNotificationDelivery> findDue(@Param("now") Instant now, Pageable pageable);
}
