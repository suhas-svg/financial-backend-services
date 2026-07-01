package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.ScheduledTransferRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTransferRunResponse {

    private String runId;
    private String scheduleId;
    private Instant scheduledFor;
    private Instant startedAt;
    private Instant completedAt;
    private ScheduledTransferRunStatus status;
    private String transactionId;
    private String idempotencyKey;
    private String failureReason;
}
