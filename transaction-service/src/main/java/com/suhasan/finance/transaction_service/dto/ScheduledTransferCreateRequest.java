package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.DstGapPolicy;
import com.suhasan.finance.transaction_service.entity.DstOverlapPolicy;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferFrequency;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
public class ScheduledTransferCreateRequest {
    @NotBlank private String fromAccountId;
    @NotBlank private String toAccountId;
    @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") private BigDecimal amount;
    @Pattern(regexp = "USD|EUR|GBP|INR") private String currency = "USD";
    @Size(max = 500) private String description;
    @Size(max = 100) private String reference;
    @NotNull private ScheduledTransferType scheduleType;
    private ScheduledTransferFrequency frequency;
    private Instant firstRunAt;
    private LocalDateTime firstRunLocal;
    @Size(max = 64) private String timeZone = "UTC";
    private DstOverlapPolicy dstOverlapPolicy = DstOverlapPolicy.EARLIER;
    private DstGapPolicy dstGapPolicy = DstGapPolicy.SHIFT_FORWARD;
    private Instant endAt;
}
