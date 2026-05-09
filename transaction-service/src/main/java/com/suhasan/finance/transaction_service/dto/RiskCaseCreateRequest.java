package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.RiskCasePriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskCaseCreateRequest {
    private String title;
    private RiskCasePriority priority;
    private String reason;
}
