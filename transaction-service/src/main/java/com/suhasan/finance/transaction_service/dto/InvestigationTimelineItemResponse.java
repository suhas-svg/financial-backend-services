package com.suhasan.finance.transaction_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationTimelineItemResponse {
    private String itemId;
    private String itemType;
    private String title;
    private String description;
    private String severity;
    private String status;
    private String userId;
    private String transactionId;
    private String accountId;
    private String alertId;
    private String caseId;
    private String amount;
    private String currency;
    private LocalDateTime createdAt;
    private Map<String, Object> metadata;
}
