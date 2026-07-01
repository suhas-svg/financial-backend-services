package com.suhasan.finance.transaction_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scheduled_transfers", indexes = {
        @Index(name = "idx_scheduled_transfers_user_id", columnList = "user_id"),
        @Index(name = "idx_scheduled_transfers_status_next_run", columnList = "status,next_run_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledTransfer {

    @Id
    @Column(name = "schedule_id", length = 36)
    private String scheduleId;

    @NotBlank
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @NotBlank
    @Column(name = "from_account_id", nullable = false, length = 64)
    private String fromAccountId;

    @NotBlank
    @Column(name = "to_account_id", nullable = false, length = 64)
    private String toAccountId;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotBlank
    @Column(nullable = false, length = 3)
    private String currency;

    @Size(max = 500)
    @Column(length = 500)
    private String description;

    @Size(max = 100)
    @Column(length = 100)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 32)
    private ScheduledTransferType scheduleType;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ScheduledTransferFrequency frequency;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScheduledTransferStatus status;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        if (scheduleId == null) {
            scheduleId = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = ScheduledTransferStatus.ACTIVE;
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
