package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name="spending_limit_reservations") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SpendingLimitReservation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long reservationId;
    @Column(nullable=false) private Long accountId;
    @Column(nullable=false) private String operationType;
    @Column(nullable=false) private BigDecimal amount;
    @Column(nullable=false) private LocalDate usageDate;
    @Column(nullable=false) private String idempotencyKey;
    @Column(nullable=false) private LocalDateTime createdAt;
}
