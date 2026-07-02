package com.suhasan.finance.account_service.dto;

import com.suhasan.finance.account_service.entity.BeneficiaryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeneficiaryResponse {
    private String beneficiaryId;
    private String userId;
    private String displayName;
    private String destinationAccountId;
    private String currency;
    private String nickname;
    private String notes;
    private BeneficiaryStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime disabledAt;
    private Long version;
}
