package com.suhasan.finance.account_service.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public final class SpendingLimitDtos {
    private SpendingLimitDtos() {
    }

    public record UpdateRequest(
            @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal transferDailyLimit,
            @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal withdrawalDailyLimit,
            String credential) {
    }

    public record ReserveRequest(
            @NotBlank @Size(max = 20) String operationType,
            @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 160) String idempotencyKey,
            @NotBlank @Size(max = 255) String userId,
            @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @Size(max = 160) String transactionCorrelation) {
        public ReserveRequest(String operationType, BigDecimal amount, String idempotencyKey, String userId) {
            this(operationType, amount, idempotencyKey, userId, null, null);
        }
    }

    public record ReservationTransitionRequest(
            @NotBlank @Size(max = 255) String userId,
            @Size(max = 160) String transactionCorrelation,
            @Size(max = 120) String outcome) {
    }

    public record LimitResponse(
            Long accountId,
            String currency,
            BigDecimal transferDailyLimit,
            BigDecimal withdrawalDailyLimit,
            BigDecimal transferUsedToday,
            BigDecimal withdrawalUsedToday,
            BigDecimal pendingTransferDailyLimit,
            BigDecimal pendingWithdrawalDailyLimit,
            LocalDateTime pendingEffectiveAt) {
    }

    public record ReserveResponse(
            boolean allowed,
            boolean replay,
            String currency,
            BigDecimal dailyLimit,
            BigDecimal dailyUsed,
            BigDecimal remaining,
            String reason,
            Long reservationId,
            String transactionCorrelation,
            BigDecimal amount,
            String fingerprint,
            String state,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime expiresAt,
            String outcome) {
        public ReserveResponse(boolean allowed, boolean replay, String currency, BigDecimal dailyLimit,
                               BigDecimal dailyUsed, BigDecimal remaining, String reason) {
            this(allowed, replay, currency, dailyLimit, dailyUsed, remaining, reason,
                    null, null, null, null, null, null, null, null, null);
        }
    }

    public record AuditResponse(
            Long eventId,
            Long accountId,
            String userId,
            String eventType,
            String operationType,
            BigDecimal amount,
            BigDecimal dailyLimit,
            BigDecimal dailyUsed,
            String details,
            LocalDateTime createdAt) {
    }

    public record AuditPage(List<AuditResponse> content) {
        public AuditPage {
            content = List.copyOf(content);
        }
    }
}
