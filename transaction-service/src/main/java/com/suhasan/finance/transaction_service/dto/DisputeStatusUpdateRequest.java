package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.DisputeStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisputeStatusUpdateRequest {
    @NotNull
    private DisputeStatus status;

    @Size(max = 1000)
    private String resolutionNote;
}
