package com.suhasan.finance.transaction_service.outcome.web;

import com.suhasan.finance.transaction_service.outcome.fx.FxRateQuote;

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
    public enum OutcomeType { BALANCE_FLOOR, SCHEDULED_OBLIGATION }

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
            @NotNull @Size(max = 16) List<@Valid ShockInput> shocks,
            OutcomeType outcomeType,
            @Size(max = 36) String protectedScheduleId,
            @PositiveOrZero Long protectedScheduleVersion) {
        public ScenarioRequest(String name, List<String> accountIds, String currency, String timeZone,
                               LocalDate horizonStart, int horizonDays, BigDecimal protectedMinimum,
                               List<AssumptionInput> assumptions, List<ShockInput> shocks) {
            this(name, accountIds, currency, timeZone, horizonStart, horizonDays, protectedMinimum,
                    assumptions, shocks, OutcomeType.BALANCE_FLOOR, null, null);
        }

        public OutcomeType effectiveOutcomeType() {
            return outcomeType == null ? OutcomeType.BALANCE_FLOOR : outcomeType;
        }
    }

    public record GuardrailAcceptRequest(boolean confirmed) {}
    public record RepairDraftSelectRequest(boolean confirmed) {}

    public record GuardrailTermsResponse(
            String version, String hash, String title, String summary,
            List<String> confirmations, boolean backgroundExecution) {}

    public record GuardrailConsentRequest(
            boolean confirmed,
            @NotBlank @Size(max = 64) String termsVersion,
            @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String termsHash,
            @NotBlank @Size(max = 64) String fundingAccountId,
            @NotBlank @Size(max = 64) String protectedAccountId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal maxActionAmount,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal totalLimit,
            @Min(1) @Max(100) int maxExecutions,
            @NotNull Instant expiresAt) {}

    public record GuardrailActivationRequest(@NotBlank String proof) {}
    public record GuardrailLifecycleRequest(@NotBlank @Size(max = 500) String reason) {}
    public record GuardrailExecutionRequest(
            boolean confirmed,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}
    public record GuardrailExecutionAuthorizationRequest(@NotBlank String proof) {}
    public record GuardrailControlUpdateRequest(boolean executionEnabled,
                                                @NotBlank @Size(max = 500) String reason) {}

    public record LedgerAccountSnapshot(
            String accountId, String currency, BigDecimal availableBalance,
            long projectionVersion, Instant capturedAt,
            BigDecimal baseAvailableBalance, String baseCurrency, FxRateQuote fxQuote) {
        public LedgerAccountSnapshot(String accountId, String currency, BigDecimal availableBalance,
                                     long projectionVersion, Instant capturedAt) {
            this(accountId, currency, availableBalance, projectionVersion, capturedAt,
                    availableBalance, currency, null);
        }
    }

    public record ScheduledCashflowSnapshot(
            String eventId, String scheduleId, Instant scheduledFor, LocalDate date, BigDecimal amount,
            String currency, String status, String cadence, String evaluationTimeZone,
            String label, String fromAccountId, String toAccountId,
            BigDecimal sourceAmount, String sourceCurrency, FxRateQuote fxQuote,
            Long scheduleVersion, String scheduleOwnerId, String sourceTimeZone,
            LocalDate dueLocalDate, boolean sourceOwnedByCustomer, boolean destinationOwnedByCustomer,
            boolean repairEligible, String repairIneligibilityReason) {
        public ScheduledCashflowSnapshot(String eventId, String scheduleId, Instant scheduledFor, LocalDate date,
                                         BigDecimal amount, String currency, String status, String cadence,
                                         String evaluationTimeZone, String label, String fromAccountId, String toAccountId) {
            this(eventId, scheduleId, scheduledFor, date, amount, currency, status, cadence,
                    evaluationTimeZone, label, fromAccountId, toAccountId, amount, currency, null,
                    0L, null, evaluationTimeZone, date, true, false, false,
                    "Schedule flexibility was not established");
        }
    }

    public record ProtectedObligationSnapshot(
            String scheduleId, long scheduleVersion, String status, String ownerId,
            String fromAccountId, String toAccountId,
            boolean sourceOwnedByCustomer, boolean destinationOwnedByCustomer,
            BigDecimal amount, String currency, String scheduleType, String cadence,
            Instant dueAt, LocalDate dueLocalDate, String sourceTimeZone,
            String evaluationTimeZone, Instant endAt, long sourceProjectionVersion,
            Instant capturedAt, boolean valid, String invalidReason) {}

    public record SourceSnapshot(
            BigDecimal startingAvailableBalance, String baseCurrency,
            List<LedgerAccountSnapshot> ledgerAccounts,
            List<ScheduledCashflowSnapshot> scheduledCashflows,
            ProtectedObligationSnapshot protectedObligation,
            List<FxRateQuote> fxQuotes,
            boolean executableFx,
            String sourceFingerprint) {
        public SourceSnapshot(BigDecimal startingAvailableBalance, String baseCurrency,
                              List<LedgerAccountSnapshot> ledgerAccounts,
                              List<ScheduledCashflowSnapshot> scheduledCashflows,
                              List<FxRateQuote> fxQuotes, boolean executableFx, String sourceFingerprint) {
            this(startingAvailableBalance, baseCurrency, ledgerAccounts, scheduledCashflows,
                    null, fxQuotes, executableFx, sourceFingerprint);
        }
    }

    public record TimelineEvent(
            String eventId, LocalDate date, BigDecimal amount, String source,
            String label, boolean flexible, boolean critical,
            String scheduleId, boolean protectedObligation) {
        public TimelineEvent(String eventId, LocalDate date, BigDecimal amount, String source,
                             String label, boolean flexible, boolean critical) {
            this(eventId, date, amount, source, label, flexible, critical, null, false);
        }
    }

    public record TimelineDay(LocalDate date, BigDecimal openingBalance,
                              List<TimelineEvent> events, BigDecimal closingBalance) {}

    public record ForecastProof(
            boolean safe, BigDecimal startingBalance, BigDecimal protectedMinimum,
            LocalDate failureDate, BigDecimal lowestBalance, BigDecimal closingBalance,
            List<TimelineEvent> triggeringEvents, List<TimelineDay> timeline,
            boolean balanceFloorSatisfied, boolean protectedObligationSatisfied,
            List<InvariantBreach> invariantBreaches) {
        public ForecastProof(boolean safe, BigDecimal startingBalance, BigDecimal protectedMinimum,
                             LocalDate failureDate, BigDecimal lowestBalance, BigDecimal closingBalance,
                             List<TimelineEvent> triggeringEvents, List<TimelineDay> timeline) {
            this(safe, startingBalance, protectedMinimum, failureDate, lowestBalance, closingBalance,
                    triggeringEvents, timeline, safe, true, List.of());
        }
    }

    public record InvariantBreach(String type, LocalDate date, String eventId, String scheduleId,
                                  BigDecimal balanceBefore, BigDecimal requiredAmount, BigDecimal shortfall,
                                  String explanation) {}

    public record AppliedShock(String shockId, ShockType type, String label,
                               String targetAssumptionId, BigDecimal severityScore) {}

    public record FailureProof(
            boolean failureFound, boolean baselineFailure, Integer minimalShockCount,
            List<AppliedShock> appliedShocks, LocalDate failureDate,
            BigDecimal lowestBalance, List<TimelineEvent> triggeringEvents,
            List<TimelineDay> timeline, String minimalityExplanation) {}

    public record RepairAction(String actionId, String type, BigDecimal amount,
                               List<String> affectedEventIds, String explanation,
                               String targetScheduleId, LocalDate effectiveDate, BigDecimal originalAmount,
                               int disruptionScore, boolean previewOnly) {
        public RepairAction(String actionId, String type, BigDecimal amount,
                            List<String> affectedEventIds, String explanation) {
            this(actionId, type, amount, affectedEventIds, explanation,
                    null, null, null, 0, true);
        }
    }

    public record RepairRankingFactors(boolean restoresAllInvariants, int actionCount,
                                       int disruptionScore, BigDecimal moneyMovedOrDeferred,
                                       List<String> stableActionIds) {}

    public record RepairAlternative(int rank, String alternativeId, List<RepairAction> actions,
                                    ForecastProof replay, RepairRankingFactors rankingFactors,
                                    String certificateHash, String explanation) {}

    public record RejectedRepairCandidate(String candidateId, String type, String targetId,
                                          String reasonCode, String explanation) {}

    public record RepairPlan(BigDecimal maximumShortfall, List<RepairAction> selectedRepairs,
                             boolean verifiedInModel, String minimalityExplanation,
                             List<RepairAlternative> alternatives,
                             List<RejectedRepairCandidate> rejectedCandidates,
                             int evaluatedCombinations, boolean searchCapped,
                             int maxCombinationSize, int maxEvaluatedCombinations,
                             String engineVersion, String certificateHash) {
        public RepairPlan(BigDecimal maximumShortfall, List<RepairAction> selectedRepairs,
                          boolean verifiedInModel, String minimalityExplanation) {
            this(maximumShortfall, selectedRepairs, verifiedInModel, minimalityExplanation,
                    List.of(), List.of(), 0, false, 0, 0, "outcome-v1", null);
        }
    }

    public record SimulationProof(
            ForecastProof baseline, FailureProof reverseStress, RepairPlan repair,
            int evaluatedCombinations, boolean searchCapped) {}

    public record GuardrailResponse(
            String guardrailId, String type, BigDecimal thresholdAmount, String currency,
            List<String> accountIds, Instant expiresAt, String status,
            String previewText, Instant acceptedAt, GuardrailPolicyResponse policy,
            Integer alternativeRank, List<RepairAction> candidateActions,
            String replayCertificateHash, RepairRankingFactors rankingFactors,
            Instant previewSelectedAt) {
        public GuardrailResponse(String guardrailId, String type, BigDecimal thresholdAmount, String currency,
                                 List<String> accountIds, Instant expiresAt, String status,
                                 String previewText, Instant acceptedAt) {
            this(guardrailId, type, thresholdAmount, currency, accountIds, expiresAt, status,
                    previewText, acceptedAt, null, null, List.of(), null, null, null);
        }
    }

    public record GuardrailControlResponse(
            boolean executionEnabled, String reason, String changedBy, Instant updatedAt) {}

    public record GuardrailPolicyResponse(
            String policyId, String guardrailId, String fundingAccountId, String protectedAccountId,
            String currency, BigDecimal triggerThreshold, BigDecimal maxActionAmount,
            BigDecimal totalLimit, BigDecimal totalExecuted, BigDecimal totalReserved,
            int maxExecutions, int executionCount, String termsVersion, String termsHash,
            String status, String effectiveStatus, Instant expiresAt, Instant consentedAt,
            Instant activatedAt, Instant suspendedAt, String suspensionReason,
            Instant revokedAt, String revocationReason, String activationChallengeId,
            Instant activationChallengeExpiresAt, boolean executionEnabled,
            String executionControlReason, boolean requiresReconsent,
            NotificationDeliveryEvidence notificationDelivery) {}

    public record GuardrailExecutionResponse(
            String executionId, String guardrailId, String policyId, BigDecimal amount, String currency,
            String status, String transactionId, boolean authorizationRequired,
            String authorizationChallengeId, Instant authorizationExpiresAt,
            String lastError, Instant createdAt, Instant completedAt,
            NotificationDeliveryEvidence notificationDelivery) {}

    public record GuardrailAuditEventResponse(
            String eventId, String eventType, String guardrailId, String fieldsJson, Instant createdAt) {}

    public record GuardrailOperatorPolicyResponse(
            String userId, String scenarioId, GuardrailPolicyResponse policy) {}

    public record GuardrailControlEventResponse(
            String eventId, boolean executionEnabled, String reason, String actor, Instant createdAt) {}

    public record ScenarioSummary(
            String scenarioId, String name, int version, String status, String currency,
            LocalDate horizonStart, int horizonDays, BigDecimal protectedMinimum,
            boolean baselineSafe, Instant updatedAt, OutcomeType outcomeType,
            String protectedScheduleId) {
        public ScenarioSummary(String scenarioId, String name, int version, String status, String currency,
                               LocalDate horizonStart, int horizonDays, BigDecimal protectedMinimum,
                               boolean baselineSafe, Instant updatedAt) {
            this(scenarioId, name, version, status, currency, horizonStart, horizonDays,
                    protectedMinimum, baselineSafe, updatedAt, OutcomeType.BALANCE_FLOOR, null);
        }
    }

    public record ScenarioResponse(
            String scenarioId, String name, int version, String status, String currency,
            String timeZone, LocalDate horizonStart, int horizonDays,
            BigDecimal protectedMinimum, List<String> accountIds,
            List<AssumptionInput> assumptions, List<ShockInput> shocks,
            SourceSnapshot sourceSnapshot, SimulationProof simulation,
            List<GuardrailResponse> guardrails, Instant createdAt,
            OutcomeType outcomeType, String protectedScheduleId,
            Long protectedScheduleVersion) {}

    public record NotificationDeliveryEvidence(
            String deliveryId, String state, int attemptCount, Instant nextAttemptAt,
            Instant deliveredAt, Instant terminalAt, Instant slaEscalatedAt,
            String lastError, String dedupeKey) {}

    public record DivergenceResponse(
            String scenarioId, String previousSourceFingerprint, String currentSourceFingerprint,
            String evaluationEventId, String warningEventId,
            boolean diverged, boolean protectionAtRisk, boolean warningAcknowledged, boolean notificationEmitted,
            NotificationDeliveryEvidence notificationDelivery,
            SimulationProof freshSimulation, Instant checkedAt) {}

    public record WarningAcknowledgementResponse(
            String acknowledgementEventId, String warningEventId,
            boolean acknowledged, Instant acknowledgedAt) {}
}
