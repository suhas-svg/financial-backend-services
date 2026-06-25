package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_balance_projections")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LedgerBalanceProjection {

    @Id
    @Column(name = "ledger_account_id")
    private UUID ledgerAccountId;

    @Column(name = "posted_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal postedBalance;

    @Column(name = "pending_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal pendingBalance;

    @Column(name = "pending_debits", nullable = false, precision = 19, scale = 2)
    private BigDecimal pendingDebits;

    @Column(name = "pending_credits", nullable = false, precision = 19, scale = 2)
    private BigDecimal pendingCredits;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "last_event_sequence", nullable = false)
    private long lastEventSequence;

    @Column(name = "projection_version", nullable = false)
    private long projectionVersion;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static LedgerBalanceProjection open(UUID ledgerAccountId, BigDecimal postedBalance) {
        if (ledgerAccountId == null || postedBalance == null) {
            throw new IllegalArgumentException("Ledger account and posted balance are required");
        }
        LedgerBalanceProjection projection = new LedgerBalanceProjection();
        projection.ledgerAccountId = ledgerAccountId;
        projection.postedBalance = postedBalance;
        projection.pendingDebits = BigDecimal.ZERO;
        projection.pendingCredits = BigDecimal.ZERO;
        projection.recalculate();
        projection.updatedAt = LocalDateTime.now();
        return projection;
    }

    public void reserveDebit(BigDecimal amount, long eventSequence) {
        requirePositive(amount);
        requireNextEvent(eventSequence);
        if (availableBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient available balance");
        }
        pendingDebits = pendingDebits.add(amount);
        advance(eventSequence);
    }

    public void reserveDebitAllowNegative(BigDecimal amount, long eventSequence) {
        requirePositive(amount);
        requireNextEvent(eventSequence);
        pendingDebits = pendingDebits.add(amount);
        advance(eventSequence);
    }

    public void reserveCredit(BigDecimal amount, long eventSequence) {
        requirePositive(amount);
        requireNextEvent(eventSequence);
        pendingCredits = pendingCredits.add(amount);
        advance(eventSequence);
    }

    public void postPendingDebit(BigDecimal amount, long eventSequence) {
        requirePending(amount, pendingDebits, "debit");
        requireNextEvent(eventSequence);
        pendingDebits = pendingDebits.subtract(amount);
        postedBalance = postedBalance.subtract(amount);
        advance(eventSequence);
    }

    public void postPendingCredit(BigDecimal amount, long eventSequence) {
        requirePending(amount, pendingCredits, "credit");
        requireNextEvent(eventSequence);
        pendingCredits = pendingCredits.subtract(amount);
        postedBalance = postedBalance.add(amount);
        advance(eventSequence);
    }

    public void releasePendingDebit(BigDecimal amount, long eventSequence) {
        requirePending(amount, pendingDebits, "debit");
        requireNextEvent(eventSequence);
        pendingDebits = pendingDebits.subtract(amount);
        advance(eventSequence);
    }

    public void releasePendingCredit(BigDecimal amount, long eventSequence) {
        requirePending(amount, pendingCredits, "credit");
        requireNextEvent(eventSequence);
        pendingCredits = pendingCredits.subtract(amount);
        advance(eventSequence);
    }

    private void requireNextEvent(long eventSequence) {
        if (eventSequence <= lastEventSequence) {
            throw new IllegalArgumentException("Projection event sequence must increase");
        }
    }

    private void requirePending(BigDecimal amount, BigDecimal pending, String direction) {
        requirePositive(amount);
        if (pending.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Pending " + direction + " is smaller than requested amount");
        }
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Projection amount must be positive");
        }
    }

    private void advance(long eventSequence) {
        lastEventSequence = eventSequence;
        projectionVersion++;
        updatedAt = LocalDateTime.now();
        recalculate();
    }

    private void recalculate() {
        pendingBalance = pendingCredits.subtract(pendingDebits);
        availableBalance = postedBalance.subtract(pendingDebits);
    }
}
