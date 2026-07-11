package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="spending_limit_audit_events") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SpendingLimitAuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long eventId;
    @Column(nullable=false) private Long accountId;
    @Column(nullable=false) private String userId;
    @Column(nullable=false) private String eventType;
    private String operationType;
    private BigDecimal amount;
    private BigDecimal dailyLimit;
    private BigDecimal dailyUsed;
    private String details;
    @Column(nullable=false) private LocalDateTime createdAt;
}
