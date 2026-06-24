// // AccountRequest.java
// package com.suhasan.finance.account_service.dto;

// import jakarta.validation.constraints.*;
// import lombok.Data;
// import java.math.BigDecimal;
// import java.time.LocalDate;

// @Data
// public class AccountRequest {
//     @NotBlank
//     private String ownerId;

//     @NotNull @PositiveOrZero
//     private BigDecimal balance;

//     // optional fields for subtypes...
//     private Double interestRate;
//     private BigDecimal creditLimit;
//     private LocalDate dueDate;
// }
package com.suhasan.finance.account_service.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AccountRequest {
    @NotBlank(message = "Account type is required")
    private String accountType;

    @NotBlank(message = "Owner ID is required")
    private String ownerId;

    @NotNull(message = "Balance is required")
    @PositiveOrZero(message = "Balance must be zero or positive")
    private BigDecimal balance;

    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a three-letter uppercase code")
    private String currency = "USD";

    @PositiveOrZero(message = "Interest rate must be zero or positive")
    private Double interestRate;

    @PositiveOrZero(message = "Credit limit must be zero or positive")
    private BigDecimal creditLimit;

    @Future(message = "Due date must be in the future")
    private LocalDate dueDate;
}
