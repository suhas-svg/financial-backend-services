package com.suhasan.finance.transaction_service.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositRequest {
    
    @NotBlank(message = "Account ID is required")
    private String accountId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999.99", message = "Amount exceeds maximum limit")
    private BigDecimal amount;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @Size(max = 100, message = "Reference cannot exceed 100 characters")
    private String reference;
    
    @Pattern(regexp = "USD|EUR|GBP", message = "Currency must be USD, EUR, or GBP")
    @Builder.Default
    private String currency = "USD";
}