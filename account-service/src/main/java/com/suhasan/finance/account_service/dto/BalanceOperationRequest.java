package com.suhasan.finance.account_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceOperationRequest {

    @NotBlank(message = "Operation ID is required")
    @Size(max = 100, message = "Operation ID must not exceed 100 characters")
    private String operationId;

    @NotNull(message = "Delta is required")
    private BigDecimal delta;

    @NotBlank(message = "Transaction ID is required")
    @Size(max = 100, message = "Transaction ID must not exceed 100 characters")
    private String transactionId;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    private String reason;

    private Boolean allowNegative;
}
