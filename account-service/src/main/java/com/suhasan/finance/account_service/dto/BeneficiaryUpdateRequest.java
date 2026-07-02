package com.suhasan.finance.account_service.dto;

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
public class BeneficiaryUpdateRequest {

    @NotBlank
    @Size(max = 120)
    private String displayName;

    @Size(max = 120)
    private String nickname;

    @Size(max = 500)
    private String notes;
}
