package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_monthly_statements")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CustomerMonthlyStatement {

    @Id
    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    @Column(name = "owner_id", nullable = false, length = 255)
    private String ownerId;

    @Column(name = "external_account_id", nullable = false, length = 255)
    private String externalAccountId;

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @JdbcTypeCode(java.sql.Types.CHAR)
    private String currency;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "statement_version", nullable = false)
    private int statementVersion;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    public static CustomerMonthlyStatement create(
            LedgerAccount account,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal openingBalance,
            BigDecimal closingBalance) {
        return CustomerMonthlyStatement.builder()
                .statementId(UUID.randomUUID())
                .ledgerAccountId(account.getLedgerAccountId())
                .ownerId(account.getOwnerId())
                .externalAccountId(account.getExternalAccountId())
                .currency(account.getCurrency())
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .statementVersion(1)
                .openingBalance(openingBalance)
                .closingBalance(closingBalance)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
