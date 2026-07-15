package com.suhasan.finance.transaction_service.outcome.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class OutcomeProtectionDtos {
    private OutcomeProtectionDtos() {}

    public enum AssumptionType { INCOME, EXPENSE, OTHER }
    public enum ShockType { INCOME_DELAY, INCOME_REDUCTION, EXPENSE_SPIKE, PAYMENT_TIMING_SHIFT }

    public record AssumptionInput(
            @NotBlank @Size(max = 64) String id,
            @NotNull LocalDate date,
            @NotNull @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull AssumptionType type,
            @NotBlank @Size(max = 160) String label,
            boolean flexible,
            boolean critical) {}

    public record ShockInput(
            @NotBlank @Size(max = 64) String id,
            @NotNull ShockType type,
            @NotBlank @Size(max = 64) String targetAssumptionId,
            @Min(1) @Max(90) Integer days,
            @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal percentage,
            @NotBlank @Size(max = 160) String label) {}

    public record ScenarioRequest(
            @NotBlank @Size(max = 120) String name,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 64) String> accountIds,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank @Size(max = 64) String timeZone,
            @NotNull LocalDate horizonStart,
            @Min(1) @Max(90) int horizonDays,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal protectedMinimum,
            @NotNull @Size(max = 64) List<@Valid AssumptionInput> assumptions,
            @NotNull @Size(max = 16) List<@Valid ShockInput> shocks) {}

    public record GuardrailAcceptRequest(boolean confirmed) {}

    public record LedgerAccountSnapshot(
            String accountId, String currency, BigDecimal availableBalance,
            long projectionVersion, Instant capturedAt) {}

    public record ScheduledCashflowSnapshot(
            String eventId, String scheduleId, LocalDate date, BigDecimal amount,
            String label, String fromAccountId, String toAccountId) {}

    public record SourceSnapshot(
            BigDecimal startingAvailableBalance,
            List<LedgerAccountSnapshot> ledgerAccounts,
            List<ScheduledCashflowSnapshot> scheduledCashflows,
            String sourceFingerprint) {}

    public record TimelineEvent(
            String eventId, LocalDate date, BigDecimal amount, String source,
            String label, boolean flexible, boolean critical) {}

    public record TimelineDay(LocalDate date, BigDecimal openingBalance,
                              List<TimelineEvent> events, BigDecimal closingBalance) {}

    public record ForecastProof(
            boolean safe, BigDecimal startingBalance, BigDecimal protectedMinimum,
            LocalDate failureDate, BigDecimal lowestBalance, BigDecimal closingBalance,
            List<TimelineEvent> triggeringEvents, List<TimelineDay> timeline) {}

    public record AppliedShock(String shockId, ShockType type, String label,
                               String targetAssumptionId, BigDecimal severityScore) {}

    public record FailureProof(
            boolean failureFound, boolean baselineFailure, Integer minimalShockCount,
            List<AppliedShock> appliedShocks, LocalDate failureDate,
            BigDecimal lowestBalance, List<TimelineEvent> triggeringEvents,
            List<TimelineDay> timeline, String minimalityExplanation) {}

    public record RepairAction(String actionId, String type, BigDecimal amount,
                               List<String> affectedEventIds, String explanation) {}

    public record RepairPlan(BigDecimal maximumShortfall, List<RepairAction> selectedRepairs,
                             boolean verifiedInModel, String minimalityExplanation) {}

    public record SimulationProof(
            ForecastProof baseline, FailureProof reverseStress, RepairPlan repair,
            int evaluatedCombinations, boolean searchCapped) {}

    public record GuardrailResponse(
            String guardrailId, String type, BigDecimal thresholdAmount, String currency,
            List<String> accountIds, Instant expiresAt, String status,
            String previewText, Instant acceptedAt) {}

    public record ScenarioSummary(
            String scenarioId, String name, int version, String status, String currency,
            LocalDate horizonStart, int horizonDays, BigDecimal protectedMinimum,
            boolean baselineSafe, Instant updatedAt) {}

    public record ScenarioResponse(
            String scenarioId, String name, int version, String status, String currency,
            String timeZone, LocalDate horizonStart, int horizonDays,
            BigDecimal protectedMinimum, List<String> accountIds,
            List<AssumptionInput> assumptions, List<ShockInput> shocks,
            SourceSnapshot sourceSnapshot, SimulationProof simulation,
            List<GuardrailResponse> guardrails, Instant createdAt) {}

    public record DivergenceResponse(
            String scenarioId, String previousSourceFingerprint, String currentSourceFingerprint,
            boolean diverged, boolean protectionAtRisk, boolean notificationEmitted,
            SimulationProof freshSimulation, Instant checkedAt) {}
}
