package com.suhasan.finance.transaction_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditEventFilter {
    private String eventType;
    private String action;
    private String outcome;
    private String userId;
    private String transactionId;
    private LocalDateTime from;
    private LocalDateTime to;
}
