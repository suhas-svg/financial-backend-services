package com.suhasan.finance.transaction_service.outcome.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "outcome_guardrail_runtime_controls")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutcomeGuardrailRuntimeControl {
    @Id @Column(name = "control_id", length = 32) private String controlId;
    @Column(name = "execution_enabled", nullable = false) private boolean executionEnabled;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "changed_by", nullable = false, length = 128) private String changedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private Long version;

    @PrePersist void create() {
        if (updatedAt == null) updatedAt = Instant.now();
        if (version == null) version = 0L;
    }
    @PreUpdate void update() { updatedAt = Instant.now(); }
}
