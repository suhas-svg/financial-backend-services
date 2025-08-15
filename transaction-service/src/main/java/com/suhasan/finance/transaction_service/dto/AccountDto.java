package com.suhasan.finance.transaction_service.dto;

import lombok.*;
import java.math.BigDecimal;

/**
 * DTO for Account information from Account Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDto {
    
    private Long id;
    private String ownerId;
    private BigDecimal balance;
    private String accountType;
    private Boolean active;
    
    // Credit card specific fields
    private BigDecimal creditLimit;
    private BigDecimal availableCredit;
    
    // Savings account specific fields
    private BigDecimal interestRate;
}