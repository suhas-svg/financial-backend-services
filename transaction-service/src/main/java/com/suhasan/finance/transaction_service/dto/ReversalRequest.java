package com.suhasan.finance.transaction_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReversalRequest {
    
    @NotBlank(message = "Reason is required for transaction reversal")
    @Size(max = 500, message = "Reversal reason cannot exceed 500 characters")
    private String reason;
    
    @Size(max = 100, message = "Reference cannot exceed 100 characters")
    private String reference;
}