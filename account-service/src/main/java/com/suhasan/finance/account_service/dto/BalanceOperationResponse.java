package com.suhasan.finance.account_service.dto;

import com.suhasan.finance.account_service.entity.BalanceOperationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceOperationResponse {

    private Long accountId;
    private String operationId;
    private boolean applied;
    private BigDecimal newBalance;
    private Long version;
    private BalanceOperationStatus status;
}
