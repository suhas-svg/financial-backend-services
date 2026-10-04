package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Immutable record of one account status transition.
 *
 * <p>The accounts row carries only the CURRENT status, so a freeze leaves no history once it is
 * unfrozen. This table is the trail: who changed what, when, and why.
 */
@Entity @Table(name="account_status_audit_events") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountStatusAuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long eventId;
    @Column(nullable=false) private Long accountId;
    @Column(nullable=false, length=20) private String previousStatus;
    @Column(nullable=false, length=20) private String newStatus;
    @Column(nullable=false, length=500) private String reason;
    @Column(nullable=false, length=100) private String actor;
    @Column(nullable=false) private LocalDateTime createdAt;
}
