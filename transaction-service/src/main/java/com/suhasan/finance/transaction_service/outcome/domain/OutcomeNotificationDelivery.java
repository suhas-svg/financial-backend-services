package com.suhasan.finance.transaction_service.outcome.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "outcome_notification_deliveries")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutcomeNotificationDelivery {
    @Id @Column(name = "delivery_id", length = 36) private String deliveryId;
    @Column(name = "warning_event_id", nullable = false, unique = true, length = 36) private String warningEventId;
    @Column(name = "user_id", nullable = false, length = 128) private String userId;
    @Column(name = "scenario_id", nullable = false, length = 36) private String scenarioId;
    @Column(name = "dedupe_key", nullable = false, unique = true, length = 180) private String dedupeKey;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT") private String payloadJson;
    @Column(nullable = false, length = 32) private String state;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "first_attempt_at") private Instant firstAttemptAt;
    @Column(name = "last_attempt_at") private Instant lastAttemptAt;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "terminal_at") private Instant terminalAt;
    @Column(name = "sla_escalated_at") private Instant slaEscalatedAt;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version @Column(nullable = false) private Long version;

    @PrePersist void create() {
        if (state == null) state = "PENDING";
        if (createdAt == null) createdAt = Instant.now();
        if (nextAttemptAt == null) nextAttemptAt = createdAt;
        if (version == null) version = 0L;
    }
}
