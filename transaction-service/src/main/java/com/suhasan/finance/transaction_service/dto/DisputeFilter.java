package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.DisputeReasonCode;
import com.suhasan.finance.transaction_service.entity.DisputeStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DisputeFilter {
    private DisputeStatus status;
    private DisputeReasonCode reasonCode;
    private String assignedTo;
    private String userId;
    private String transactionId;
    private LocalDateTime from;
    private LocalDateTime to;
}
