package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.DisputeReasonCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisputeCreateRequest {
    @NotBlank
    private String transactionId;

    @NotNull
    private DisputeReasonCode reasonCode;

    @NotBlank
    @Size(max = 1000)
    private String description;
}
