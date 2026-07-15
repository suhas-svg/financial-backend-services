package com.suhasan.finance.transaction_service.outcome.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "outcome_domain_events")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutcomeDomainEvent {
    @Id @Column(name = "event_id", length = 36)
    private String eventId;
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(name = "scenario_id", nullable = false, length = 36)
    private String scenarioId;
    @Column(name = "scenario_version", nullable = false)
    private int scenarioVersion;
    @Column(name = "result_id", length = 36)
    private String resultId;
    @Column(name = "guardrail_id", length = 36)
    private String guardrailId;
    @Column(name = "dedupe_key", nullable = false, length = 180)
    private String dedupeKey;
    @Column(name = "fields_json", nullable = false, columnDefinition = "TEXT")
    private String fieldsJson;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void create() { if (createdAt == null) createdAt = Instant.now(); }
}
