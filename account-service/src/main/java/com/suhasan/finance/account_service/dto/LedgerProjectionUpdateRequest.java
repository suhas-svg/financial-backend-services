package com.suhasan.finance.account_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LedgerProjectionUpdateRequest(
        @NotNull BigDecimal postedBalance,
        @NotNull BigDecimal pendingBalance,
        @NotNull BigDecimal availableBalance,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @Positive long version,
        @NotBlank String sourceEventId,
        @NotNull LocalDateTime updatedAt) {
}
