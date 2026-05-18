// AccountResponse.java
package com.suhasan.finance.account_service.dto;

import com.suhasan.finance.account_service.entity.AccountStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AccountResponse {
    private Long id;
    private String ownerId;
    private BigDecimal balance;
    private LocalDate createdAt;
    private String accountType;
    private AccountStatus status;
    private String statusReason;
    private LocalDateTime statusUpdatedAt;
    private String statusUpdatedBy;
    private Double interestRate;
    private BigDecimal creditLimit;
    private LocalDate dueDate;
}
