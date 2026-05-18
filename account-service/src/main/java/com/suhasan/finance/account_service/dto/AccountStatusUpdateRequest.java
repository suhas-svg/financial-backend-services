package com.suhasan.finance.account_service.dto;

import com.suhasan.finance.account_service.entity.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private AccountStatus status;

    @NotBlank(message = "Status reason is required")
    private String reason;
}
