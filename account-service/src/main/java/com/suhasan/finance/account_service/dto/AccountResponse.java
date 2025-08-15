// AccountResponse.java
package com.suhasan.finance.account_service.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AccountResponse {
    private Long id;
    private String ownerId;
    private BigDecimal balance;
    private LocalDate createdAt;
    private String accountType;
    private Double interestRate;
    private BigDecimal creditLimit;
    private LocalDate dueDate;
}
