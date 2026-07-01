package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.ScheduledTransferFrequency;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferRunStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTransferResponse {

    private String scheduleId;
    private String userId;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String reference;
    private ScheduledTransferType scheduleType;
    private ScheduledTransferFrequency frequency;
    private Instant nextRunAt;
    private Instant endAt;
    private ScheduledTransferStatus status;
    private Instant lastRunAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private ScheduledTransferRunStatus lastRunStatus;
    private String lastRunFailureReason;
    private String lastTransactionId;
}
