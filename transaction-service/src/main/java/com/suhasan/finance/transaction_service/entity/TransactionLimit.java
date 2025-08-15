package com.suhasan.finance.transaction_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "transaction_limits", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"accountType", "transactionType"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class TransactionLimit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(nullable = false)
    private String accountType; // CHECKING, SAVINGS, CREDIT
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;
    
    @PositiveOrZero
    @Column(precision = 19, scale = 2)
    private BigDecimal dailyLimit;
    
    @PositiveOrZero
    @Column(precision = 19, scale = 2)
    private BigDecimal monthlyLimit;
    
    @PositiveOrZero
    @Column(precision = 19, scale = 2)
    private BigDecimal perTransactionLimit;
    
    @PositiveOrZero
    private Integer dailyCount;
    
    @PositiveOrZero
    private Integer monthlyCount;
    
    @Builder.Default
    private Boolean active = true;
}