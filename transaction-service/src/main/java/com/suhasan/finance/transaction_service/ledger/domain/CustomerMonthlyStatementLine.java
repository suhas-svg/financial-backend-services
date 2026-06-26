package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer_monthly_statement_lines")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CustomerMonthlyStatementLine {

    @Id
    @Column(name = "line_id", nullable = false)
    private UUID lineId;

    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    @Column(name = "journal_id", nullable = false)
    private UUID journalId;

    @Column(name = "line_sequence", nullable = false)
    private int lineSequence;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "description", length = 500)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "running_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal runningBalance;

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @JdbcTypeCode(java.sql.Types.CHAR)
    private String currency;

    public static CustomerMonthlyStatementLine create(
            UUID statementId,
            UUID journalId,
            int lineSequence,
            LocalDate effectiveDate,
            String description,
            BigDecimal amount,
            BigDecimal runningBalance,
            String currency) {
        return CustomerMonthlyStatementLine.builder()
                .lineId(UUID.randomUUID())
                .statementId(statementId)
                .journalId(journalId)
                .lineSequence(lineSequence)
                .effectiveDate(effectiveDate)
                .description(description)
                .amount(amount)
                .runningBalance(runningBalance)
                .currency(currency)
                .build();
    }
}
