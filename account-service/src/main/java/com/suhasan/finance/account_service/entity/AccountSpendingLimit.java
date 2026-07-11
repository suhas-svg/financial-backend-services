package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "account_spending_limits") @Getter @Setter
public class AccountSpendingLimit {
    @Id private Long accountId;
    @Column(nullable=false) private BigDecimal transferDailyLimit = new BigDecimal("10000.00");
    @Column(nullable=false) private BigDecimal withdrawalDailyLimit = new BigDecimal("2000.00");
    private BigDecimal pendingTransferDailyLimit;
    private BigDecimal pendingWithdrawalDailyLimit;
    private LocalDateTime pendingEffectiveAt;
    @Column(nullable=false) private LocalDateTime updatedAt;
    @Column(nullable=false) private String updatedBy;
    @Version private Long version;
}
