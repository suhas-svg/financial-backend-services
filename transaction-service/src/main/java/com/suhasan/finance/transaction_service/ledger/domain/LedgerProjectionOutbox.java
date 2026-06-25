package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_projection_outbox")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LedgerProjectionOutbox {

    @Id
    @Column(name = "outbox_id")
    private UUID outboxId;

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    @Column(name = "external_account_id", nullable = false)
    private String externalAccountId;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    @Column(name = "projection_version", nullable = false)
    private long projectionVersion;

    @Column(name = "posted_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal postedBalance;

    @Column(name = "pending_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal pendingBalance;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance;

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @JdbcTypeCode(java.sql.Types.CHAR)
    private String currency;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static LedgerProjectionOutbox create(
            UUID outboxId,
            UUID ledgerAccountId,
            String externalAccountId,
            UUID sourceEventId,
            long projectionVersion,
            BigDecimal postedBalance,
            BigDecimal pendingBalance,
            BigDecimal availableBalance,
            String currency,
            LocalDateTime createdAt) {
        LedgerProjectionOutbox message = new LedgerProjectionOutbox();
        message.outboxId = outboxId;
        message.ledgerAccountId = ledgerAccountId;
        message.externalAccountId = externalAccountId;
        message.sourceEventId = sourceEventId;
        message.projectionVersion = projectionVersion;
        message.postedBalance = postedBalance;
        message.pendingBalance = pendingBalance;
        message.availableBalance = availableBalance;
        message.currency = currency;
        message.createdAt = createdAt;
        message.nextAttemptAt = createdAt;
        return message;
    }

    public void markDelivered(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
        this.lastError = null;
    }

    public void markTerminalFailure(String error, LocalDateTime now) {
        this.deliveredAt = now;
        this.lastError = "TERMINAL: " + truncate(error);
        this.attemptCount++;
    }

    public void markTransientFailure(String error, LocalDateTime nextAttemptAt) {
        this.attemptCount++;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = truncate(error);
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "delivery failed";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
