package com.suhasan.finance.account_service.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass") // Namespace for nested DTO records.
public final class SpendingLimitDtos {
 private SpendingLimitDtos() {}
 public record UpdateRequest(@NotNull @PositiveOrZero BigDecimal transferDailyLimit,@NotNull @PositiveOrZero BigDecimal withdrawalDailyLimit,String credential) {}
 public record ReserveRequest(@NotBlank String operationType,@NotNull @Positive BigDecimal amount,@NotBlank String idempotencyKey,@NotBlank String userId) {}
 public record LimitResponse(Long accountId,String currency,BigDecimal transferDailyLimit,BigDecimal withdrawalDailyLimit,BigDecimal transferUsedToday,BigDecimal withdrawalUsedToday,BigDecimal pendingTransferDailyLimit,BigDecimal pendingWithdrawalDailyLimit,LocalDateTime pendingEffectiveAt) {}
 public record ReserveResponse(boolean allowed,boolean replay,String currency,BigDecimal dailyLimit,BigDecimal dailyUsed,BigDecimal remaining,String reason) {}
 public record AuditResponse(Long eventId,Long accountId,String userId,String eventType,String operationType,BigDecimal amount,BigDecimal dailyLimit,BigDecimal dailyUsed,String details,LocalDateTime createdAt) {}
 public record AuditPage(List<AuditResponse> content) { public AuditPage { content=List.copyOf(content); } }
}
