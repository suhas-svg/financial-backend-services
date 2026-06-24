package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerAccount {
    @Id
    @Column(name = "ledger_account_id")
    private UUID ledgerAccountId;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_kind", nullable = false)
    private LedgerAccountKind accountKind;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "external_account_id")
    private String externalAccountId;
    @Column(name = "owner_id")
    private String ownerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerAccountStatus status;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Version
    @Column(nullable = false)
    private long version;
}
