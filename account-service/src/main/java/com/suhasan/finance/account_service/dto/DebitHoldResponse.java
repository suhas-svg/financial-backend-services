package com.suhasan.finance.account_service.dto;

import com.suhasan.finance.account_service.entity.DebitHoldStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebitHoldResponse {
    private String holdId;
    private Long accountId;
    private boolean applied;
    private BigDecimal ledgerBalance;
    private BigDecimal availableBalance;
    private Long version;
    private DebitHoldStatus status;
    private String message;
}
