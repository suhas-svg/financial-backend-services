package com.suhasan.finance.transaction_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationFilter {
    private String userId;
    private String transactionId;
    private String accountId;
    private String alertId;
    private String caseId;
    private LocalDateTime from;
    private LocalDateTime to;
}
