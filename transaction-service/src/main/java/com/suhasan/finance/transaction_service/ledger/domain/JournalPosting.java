package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "journal_postings")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JournalPosting {
    @Id
    @Column(name = "posting_id")
    private UUID postingId;
    @Column(name = "journal_id", nullable = false)
    private UUID journalId;
    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;
    @Column(name = "posting_sequence", nullable = false)
    private int postingSequence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostingDirection direction;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    private String memo;
}
