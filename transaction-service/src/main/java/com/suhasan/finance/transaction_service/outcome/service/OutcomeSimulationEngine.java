package com.suhasan.finance.transaction_service.outcome.service;

import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

@Component
public class OutcomeSimulationEngine {
    public static final String ENGINE_VERSION = "outcome-repair-v2";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final int maxCombinationSize;
    private final int maxEvaluatedCombinations;
    private final int repairMaxCombinationSize;
    private final int repairMaxEvaluatedCombinations;

    @Autowired
    public OutcomeSimulationEngine(
            @Value("${outcome-protection.search.max-combination-size:3}") int maxCombinationSize,
            @Value("${outcome-protection.search.max-evaluated-combinations:5000}") int maxEvaluatedCombinations,
            @Value("${outcome-protection.repair-search.max-combination-size:3}") int repairMaxCombinationSize,
            @Value("${outcome-protection.repair-search.max-evaluated-combinations:500}") int repairMaxEvaluatedCombinations) {
        this.maxCombinationSize = bounded(maxCombinationSize, 1, 5);
        this.maxEvaluatedCombinations = bounded(maxEvaluatedCombinations, 1, 100000);
        this.repairMaxCombinationSize = bounded(repairMaxCombinationSize, 1, 5);
        this.repairMaxEvaluatedCombinations = bounded(repairMaxEvaluatedCombinations, 1, 10000);
    }

    public OutcomeSimulationEngine(int maxCombinationSize, int maxEvaluatedCombinations) {
        this(maxCombinationSize, maxEvaluatedCombinations, 3, 500);
    }

    public record Cashflow(
            String eventId, LocalDate date, BigDecimal amount, String source,
            String label, boolean flexible, boolean critical,
            String scheduleId, boolean protectedObligation,
            boolean repairEligible, String repairIneligibilityReason) {
        public Cashflow(String eventId, LocalDate date, BigDecimal amount, String source,
                        String label, boolean flexible, boolean critical) {
            this(eventId, date, amount, source, label, flexible, critical,
                    null, false, false, null);
        }
    }

    public record ProtectionTarget(
            OutcomeType outcomeType, String protectedScheduleId,
            boolean obligationSnapshotValid, String obligationInvalidReason) {
        public static ProtectionTarget balanceFloor() {
            return new ProtectionTarget(OutcomeType.BALANCE_FLOOR, null, true, null);
        }
    }

    public SimulationProof simulate(
            BigDecimal startingBalance, BigDecimal protectedMinimum,
            LocalDate horizonStart, int horizonDays,
            List<Cashflow> cashflows, List<ShockInput> shocks) {
        return simulate(startingBalance, protectedMinimum, horizonStart, horizonDays,
                cashflows, shocks, ProtectionTarget.balanceFloor());
    }

    public SimulationProof simulate(
            BigDecimal startingBalance, BigDecimal protectedMinimum,
            LocalDate horizonStart, int horizonDays,
            List<Cashflow> cashflows, List<ShockInput> shocks,
            ProtectionTarget protectionTarget) {
        requireMoney(startingBalance, "starting balance");
        requireMoney(protectedMinimum, "protected minimum");
        if (horizonDays < 1 || horizonDays > 90) {
            throw new IllegalArgumentException("Horizon must be between 1 and 90 days");
        }
        ProtectionTarget target = protectionTarget == null ? ProtectionTarget.balanceFloor() : protectionTarget;
        List<Cashflow> ordered = cashflows.stream().sorted(cashflowOrder()).toList();
        ForecastProof baseline = forecast(startingBalance, protectedMinimum, horizonStart, horizonDays, ordered, target);
        SearchOutcome search = baseline.safe()
                ? searchFailure(startingBalance, protectedMinimum, horizonStart, horizonDays, ordered, shocks, target)
                : new SearchOutcome(baselineFailure(baseline), 0, false, ordered);
        List<Cashflow> repairCashflows = search.failure().failureFound() ? search.failureCashflows() : ordered;
        ForecastProof repairTarget = search.failure().failureFound()
                ? forecast(startingBalance, protectedMinimum, horizonStart, horizonDays, repairCashflows, target)
                : baseline;
        RepairPlan repair = compileRepairs(startingBalance, protectedMinimum, horizonStart,
                horizonDays, repairTarget, repairCashflows, target);
        return new SimulationProof(baseline, search.failure(), repair, search.evaluated(), search.capped());
    }

    private SearchOutcome searchFailure(
            BigDecimal startingBalance, BigDecimal protectedMinimum,
            LocalDate horizonStart, int horizonDays,
            List<Cashflow> baselineCashflows, List<ShockInput> shocks,
            ProtectionTarget target) {
        List<ShockInput> orderedShocks = shocks == null ? List.of() : shocks.stream()
                .sorted(Comparator.comparing(ShockInput::id)).toList();
        SearchState state = new SearchState();
        int maxSize = Math.min(maxCombinationSize, orderedShocks.size());
        for (int size = 1; size <= maxSize && !state.capped; size++) {
            evaluateShockCombinations(orderedShocks, size, 0, new ArrayList<>(), state,
                    startingBalance, protectedMinimum, horizonStart, horizonDays, baselineCashflows, target);
            if (state.best != null) break;
        }
        if (state.best == null) {
            String explanation = state.capped
                    ? "No failure was found before the configured combination cap was reached."
                    : "No submitted shock combination up to size %d caused the protected outcomes to fail."
                    .formatted(maxSize);
            return new SearchOutcome(new FailureProof(false, false, null, List.of(), null,
                    null, List.of(), List.of(), explanation), state.evaluated, state.capped, baselineCashflows);
        }
        Candidate best = state.best;
        String explanation = state.capped
                ? "A %d-shock failure was found in the bounded search; the evaluation cap was reached while comparing same-size candidates."
                .formatted(best.shocks().size())
                : "No combination with fewer than %d shocks failed; this is the lowest-severity failing set among all evaluated %d-shock combinations."
                .formatted(best.shocks().size(), best.shocks().size());
        FailureProof proof = new FailureProof(true, false, best.shocks().size(), best.applied(),
                best.forecast().failureDate(), best.forecast().lowestBalance(),
                best.forecast().triggeringEvents(), best.forecast().timeline(), explanation);
        return new SearchOutcome(proof, state.evaluated, state.capped, best.cashflows());
    }

    private void evaluateShockCombinations(
            List<ShockInput> shocks, int targetSize, int index,
            List<ShockInput> selected, SearchState state,
            BigDecimal startingBalance, BigDecimal protectedMinimum,
            LocalDate horizonStart, int horizonDays, List<Cashflow> baselineCashflows,
            ProtectionTarget target) {
        if (state.capped) return;
        if (selected.size() == targetSize) {
            if (state.evaluated >= maxEvaluatedCombinations) {
                state.capped = true;
                return;
            }
            state.evaluated++;
            Applied applied = applyShocks(baselineCashflows, selected, horizonDays);
            ForecastProof forecast = forecast(startingBalance, protectedMinimum,
                    horizonStart, horizonDays, applied.cashflows(), target);
            if (!forecast.safe()) {
                Candidate candidate = new Candidate(List.copyOf(selected), applied.appliedShocks(),
                        forecast, applied.severity(), applied.cashflows());
                if (state.best == null || candidateOrder().compare(candidate, state.best) < 0) {
                    state.best = candidate;
                }
            }
            return;
        }
        int remaining = targetSize - selected.size();
        for (int i = index; i <= shocks.size() - remaining && !state.capped; i++) {
            selected.add(shocks.get(i));
            evaluateShockCombinations(shocks, targetSize, i + 1, selected, state,
                    startingBalance, protectedMinimum, horizonStart, horizonDays, baselineCashflows, target);
            selected.remove(selected.size() - 1);
        }
    }

    private Applied applyShocks(List<Cashflow> baseline, List<ShockInput> selected, int horizonDays) {
        Map<String, Cashflow> cashflows = new LinkedHashMap<>();
        baseline.forEach(flow -> cashflows.put(flow.eventId(), flow));
        List<AppliedShock> applied = new ArrayList<>();
        BigDecimal totalSeverity = BigDecimal.ZERO;
        for (ShockInput shock : selected.stream().sorted(Comparator.comparing(ShockInput::id)).toList()) {
            Cashflow source = cashflows.get(shock.targetAssumptionId());
            if (source == null || !"ASSUMPTION".equals(source.source())) {
                throw new IllegalArgumentException("Shock %s targets an unknown assumption".formatted(shock.id()));
            }
            Cashflow changed;
            BigDecimal severity;
            switch (shock.type()) {
                case INCOME_DELAY -> {
                    requirePositive(source.amount(), "Income delay target");
                    int days = requiredDays(shock);
                    changed = copy(source, source.date().plusDays(days), source.amount());
                    severity = source.amount().multiply(BigDecimal.valueOf(days))
                            .divide(BigDecimal.valueOf(horizonDays), 2, RoundingMode.HALF_UP);
                }
                case INCOME_REDUCTION -> {
                    requirePositive(source.amount(), "Income reduction target");
                    BigDecimal percentage = requiredPercentage(shock);
                    BigDecimal reduction = money(source.amount().multiply(percentage)
                            .divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
                    changed = copy(source, source.date(), source.amount().subtract(reduction));
                    severity = reduction;
                }
                case EXPENSE_SPIKE -> {
                    requireNegative(source.amount(), "Expense spike target");
                    BigDecimal amount = money(Objects.requireNonNull(shock.amount(), "Expense spike amount is required"));
                    requirePositive(amount, "Expense spike amount");
                    changed = copy(source, source.date(), source.amount().subtract(amount));
                    severity = amount;
                }
                case PAYMENT_TIMING_SHIFT -> {
                    requireNegative(source.amount(), "Payment timing target");
                    int days = requiredDays(shock);
                    changed = copy(source, source.date().minusDays(days), source.amount());
                    severity = source.amount().abs().multiply(BigDecimal.valueOf(days))
                            .divide(BigDecimal.valueOf(horizonDays), 2, RoundingMode.HALF_UP);
                }
                default -> throw new IllegalArgumentException("Unsupported shock type");
            }
            cashflows.put(source.eventId(), changed);
            totalSeverity = totalSeverity.add(severity);
            applied.add(new AppliedShock(shock.id(), shock.type(), shock.label(),
                    shock.targetAssumptionId(), money(severity)));
        }
        return new Applied(cashflows.values().stream().sorted(cashflowOrder()).toList(),
                applied, money(totalSeverity));
    }

    private ForecastProof forecast(
            BigDecimal startingBalance, BigDecimal protectedMinimum,
            LocalDate horizonStart, int horizonDays, List<Cashflow> cashflows,
            ProtectionTarget target) {
        LocalDate horizonEnd = horizonStart.plusDays(horizonDays - 1L);
        Map<LocalDate, List<Cashflow>> byDate = new HashMap<>();
        cashflows.stream().filter(flow -> !flow.date().isBefore(horizonStart) && !flow.date().isAfter(horizonEnd))
                .forEach(flow -> byDate.computeIfAbsent(flow.date(), ignored -> new ArrayList<>()).add(flow));

        BigDecimal balance = money(startingBalance);
        BigDecimal lowest = balance;
        boolean balanceFloorSatisfied = balance.compareTo(protectedMinimum) >= 0;
        boolean obligationRequired = target.outcomeType() == OutcomeType.SCHEDULED_OBLIGATION;
        boolean obligationSatisfied = !obligationRequired || target.obligationSnapshotValid();
        boolean protectedOccurrenceSeen = false;
        LocalDate failureDate = balanceFloorSatisfied ? null : horizonStart;
        List<TimelineEvent> triggers = List.of();
        List<InvariantBreach> breaches = new ArrayList<>();
        if (!balanceFloorSatisfied) {
            breaches.add(floorBreach(horizonStart, null, null, balance, protectedMinimum));
        }
        if (obligationRequired && !target.obligationSnapshotValid()) {
            obligationSatisfied = false;
            breaches.add(new InvariantBreach("PROTECTED_OBLIGATION_MISSING_OR_CHANGED", horizonStart,
                    null, target.protectedScheduleId(), balance, null, null,
                    target.obligationInvalidReason() == null
                            ? "The protected obligation snapshot is no longer valid."
                            : target.obligationInvalidReason()));
            if (failureDate == null) failureDate = horizonStart;
        }

        List<TimelineDay> timeline = new ArrayList<>(horizonDays);
        for (int day = 0; day < horizonDays; day++) {
            LocalDate date = horizonStart.plusDays(day);
            BigDecimal opening = balance;
            List<Cashflow> dayFlows = byDate.getOrDefault(date, List.of()).stream()
                    .sorted(cashflowOrder()).toList();
            List<TimelineEvent> events = dayFlows.stream().map(this::timelineEvent).toList();
            for (Cashflow flow : dayFlows) {
                if (flow.protectedObligation()) {
                    protectedOccurrenceSeen = true;
                    BigDecimal required = flow.amount().signum() < 0 ? flow.amount().abs() : BigDecimal.ZERO;
                    if (balance.compareTo(required) < 0) {
                        obligationSatisfied = false;
                        BigDecimal shortfall = money(required.subtract(balance).max(BigDecimal.ZERO));
                        breaches.add(new InvariantBreach("PROTECTED_OBLIGATION_INSUFFICIENT_FUNDS",
                                date, flow.eventId(), flow.scheduleId(), balance, required, shortfall,
                                "The protected obligation requires %s but only %s is modeled as available immediately before it."
                                        .formatted(required.toPlainString(), balance.toPlainString())));
                        if (failureDate == null) {
                            failureDate = date;
                            triggers = List.of(timelineEvent(flow));
                        }
                    }
                }
                balance = money(balance.add(flow.amount()));
            }
            if (balance.compareTo(lowest) < 0) lowest = balance;
            if (balance.compareTo(protectedMinimum) < 0) {
                balanceFloorSatisfied = false;
                boolean already = breaches.stream().anyMatch(breach ->
                        "BALANCE_FLOOR_BREACH".equals(breach.type()) && date.equals(breach.date()));
                if (!already) {
                    breaches.add(floorBreach(date, events.isEmpty() ? null : events.getLast().eventId(),
                            events.isEmpty() ? null : events.getLast().scheduleId(), balance, protectedMinimum));
                }
                if (failureDate == null) {
                    failureDate = date;
                    triggers = events;
                }
            }
            timeline.add(new TimelineDay(date, opening, events, balance));
        }
        if (obligationRequired && target.obligationSnapshotValid() && !protectedOccurrenceSeen) {
            obligationSatisfied = false;
            breaches.add(new InvariantBreach("PROTECTED_OBLIGATION_MISSING_OR_CHANGED", horizonStart,
                    null, target.protectedScheduleId(), startingBalance, null, null,
                    "No occurrence of the protected obligation exists inside the immutable horizon snapshot."));
            if (failureDate == null) failureDate = horizonStart;
        }
        boolean safe = balanceFloorSatisfied && obligationSatisfied;
        return new ForecastProof(safe, money(startingBalance), money(protectedMinimum), failureDate,
                money(lowest), money(balance), triggers, timeline,
                balanceFloorSatisfied, obligationSatisfied, List.copyOf(breaches));
    }

    private RepairPlan compileRepairs(
            BigDecimal startingBalance, BigDecimal protectedMinimum,
            LocalDate horizonStart, int horizonDays, ForecastProof failed,
            List<Cashflow> targetCashflows, ProtectionTarget target) {
        if (failed.safe()) {
            return new RepairPlan(BigDecimal.ZERO.setScale(2), List.of(), true,
                    "Every selected protected invariant is already satisfied; no repair action is required.",
                    List.of(), List.of(), 0, false, repairMaxCombinationSize,
                    repairMaxEvaluatedCombinations, ENGINE_VERSION,
                    hash("safe|" + canonicalCashflows(targetCashflows)));
        }

        BigDecimal shortfall = failed.invariantBreaches().stream()
                .map(InvariantBreach::shortfall).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::max);
        shortfall = money(shortfall.max(protectedMinimum.subtract(failed.lowestBalance()).max(BigDecimal.ZERO)));
        if (shortfall.signum() == 0) shortfall = new BigDecimal("0.01");

        CandidateCatalog catalog = buildRepairCandidates(shortfall, horizonStart, horizonDays, targetCashflows, target);
        RepairSearchState state = new RepairSearchState();
        int maxSize = Math.min(repairMaxCombinationSize, catalog.candidates().size());
        for (int size = 1; size <= maxSize && !state.capped; size++) {
            evaluateRepairCombinations(catalog.candidates(), size, 0, new ArrayList<>(), state,
                    startingBalance, protectedMinimum, horizonStart, horizonDays, targetCashflows, target);
        }
        List<RepairAlternative> ordered = state.eligible.stream()
                .sorted(repairAlternativeOrder())
                .toList();
        List<RepairAlternative> ranked = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            RepairAlternative value = ordered.get(i);
            ranked.add(new RepairAlternative(i + 1, value.alternativeId(), value.actions(), value.replay(),
                    value.rankingFactors(), value.certificateHash(),
                    "Rank %d restores every invariant within the configured bounded search; ordering minimizes action count, disruption, money moved or deferred, then stable action IDs."
                            .formatted(i + 1)));
        }
        List<RejectedRepairCandidate> rejected = new ArrayList<>(catalog.rejected());
        rejected.addAll(state.rejected.values());
        List<RepairAction> selected = ranked.isEmpty() ? List.of() : ranked.getFirst().actions();
        String explanation = ranked.isEmpty()
                ? "No permitted repair restored every invariant inside the configured bounded search."
                : "Ranked %d replay-proven alternative(s) inside the configured bounded search caps of %d actions and %d evaluated combinations; this is not a claim of global optimality outside those caps."
                .formatted(ranked.size(), repairMaxCombinationSize, repairMaxEvaluatedCombinations);
        String certificate = hash(ENGINE_VERSION + "|" + canonicalCashflows(targetCashflows) + "|"
                + ranked.stream().map(RepairAlternative::certificateHash).toList() + "|" + rejected + "|"
                + repairMaxCombinationSize + "|" + repairMaxEvaluatedCombinations);
        return new RepairPlan(shortfall, selected, !ranked.isEmpty(), explanation,
                List.copyOf(ranked), List.copyOf(rejected), state.evaluated, state.capped,
                repairMaxCombinationSize, repairMaxEvaluatedCombinations, ENGINE_VERSION, certificate);
    }

    private CandidateCatalog buildRepairCandidates(
            BigDecimal shortfall, LocalDate horizonStart, int horizonDays,
            List<Cashflow> cashflows, ProtectionTarget target) {
        LocalDate horizonEnd = horizonStart.plusDays(horizonDays - 1L);
        List<RepairAction> candidates = new ArrayList<>();
        List<RejectedRepairCandidate> rejected = new ArrayList<>();
        candidates.add(new RepairAction("reserve-buffer:" + shortfall.toPlainString(), "RESERVE_BUFFER",
                shortfall, List.of(), "Add an explicit same-currency buffer equal to the modeled shortfall.",
                null, horizonStart, null, 1, false));

        Map<String, Cashflow> schedules = new TreeMap<>();
        cashflows.stream().filter(flow -> flow.scheduleId() != null)
                .forEach(flow -> schedules.putIfAbsent(flow.scheduleId(), flow));
        for (Cashflow flow : schedules.values()) {
            if (flow.protectedObligation() || Objects.equals(flow.scheduleId(), target.protectedScheduleId())) {
                rejected.add(new RejectedRepairCandidate("schedule:" + flow.scheduleId(), "SCHEDULE_CHANGE",
                        flow.scheduleId(), "PROTECTED_OBLIGATION",
                        "The protected obligation itself is never changed by repair search."));
                continue;
            }
            if (!flow.repairEligible()) {
                rejected.add(new RejectedRepairCandidate("schedule:" + flow.scheduleId(), "SCHEDULE_CHANGE",
                        flow.scheduleId(), "SCHEDULE_INELIGIBLE",
                        flow.repairIneligibilityReason() == null
                                ? "The schedule is not eligible for an advisory change."
                                : flow.repairIneligibilityReason()));
                continue;
            }
            if (flow.date().isBefore(horizonEnd)) {
                candidates.add(new RepairAction("shift-schedule:" + flow.scheduleId() + ":" + horizonEnd,
                        "SHIFT_OPTIONAL_SCHEDULE", flow.amount().abs(), List.of(flow.eventId()),
                        "Preview deferring this explicitly flexible, non-protected schedule to the end of the horizon.",
                        flow.scheduleId(), horizonEnd, flow.amount().abs(), 3, true));
            } else {
                rejected.add(new RejectedRepairCandidate("shift-schedule:" + flow.scheduleId(),
                        "SHIFT_OPTIONAL_SCHEDULE", flow.scheduleId(), "NO_LATER_DATE_IN_HORIZON",
                        "The occurrence is already on the final horizon date."));
            }
            BigDecimal reduction = money(shortfall.min(flow.amount().abs()));
            if (reduction.signum() > 0) {
                candidates.add(new RepairAction("reduce-schedule:" + flow.scheduleId() + ":" + reduction,
                        "REDUCE_OPTIONAL_SCHEDULE", reduction, List.of(flow.eventId()),
                        "Preview reducing this explicitly flexible, non-protected schedule by the modeled amount.",
                        flow.scheduleId(), flow.date(), flow.amount().abs(), 3, true));
            }
        }

        List<Cashflow> flexible = cashflows.stream()
                .filter(flow -> "ASSUMPTION".equals(flow.source()) && flow.flexible()
                        && !flow.critical() && flow.amount().signum() < 0)
                .sorted(Comparator.comparing(Cashflow::eventId)).toList();
        List<String> flexibleIds = flexible.stream().map(Cashflow::eventId).toList();
        BigDecimal flexibleTotal = flexible.stream().map(flow -> flow.amount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (flexibleTotal.signum() > 0) {
            BigDecimal bounded = money(shortfall.min(flexibleTotal));
            candidates.add(new RepairAction("temporary-spending-limit:" + bounded,
                    "TEMPORARY_SPENDING_LIMIT", bounded, flexibleIds,
                    "Preview a temporary discretionary-spending cap; applying it still requires the existing spending-control consent and MFA semantics.",
                    null, horizonStart, flexibleTotal, 2, true));
            candidates.add(new RepairAction("review-flexible-expenses:" + bounded,
                    "REVIEW_FLEXIBLE_EXPENSES", bounded, flexibleIds,
                    "Review the listed flexible, non-critical assumptions without changing any protected obligation.",
                    null, horizonStart, flexibleTotal, 4, true));
        } else {
            rejected.add(new RejectedRepairCandidate("temporary-spending-limit", "TEMPORARY_SPENDING_LIMIT",
                    null, "NO_FLEXIBLE_EXPENSES", "No flexible, non-critical modeled expenses support a bounded advisory limit."));
            rejected.add(new RejectedRepairCandidate("review-flexible-expenses", "REVIEW_FLEXIBLE_EXPENSES",
                    null, "NO_FLEXIBLE_EXPENSES", "No flexible, non-critical modeled expenses are available for review."));
        }
        return new CandidateCatalog(candidates.stream().sorted(Comparator.comparing(RepairAction::actionId)).toList(),
                List.copyOf(rejected));
    }

    private void evaluateRepairCombinations(
            List<RepairAction> candidates, int targetSize, int index,
            List<RepairAction> selected, RepairSearchState state,
            BigDecimal startingBalance, BigDecimal protectedMinimum,
            LocalDate horizonStart, int horizonDays, List<Cashflow> baseCashflows,
            ProtectionTarget target) {
        if (state.capped) return;
        if (selected.size() == targetSize) {
            if (state.evaluated >= repairMaxEvaluatedCombinations) {
                state.capped = true;
                return;
            }
            state.evaluated++;
            List<RepairAction> actions = selected.stream().sorted(Comparator.comparing(RepairAction::actionId)).toList();
            List<Cashflow> repaired = applyRepairs(baseCashflows, actions, horizonStart);
            ForecastProof replay = forecast(startingBalance, protectedMinimum, horizonStart, horizonDays, repaired, target);
            String actionIds = String.join("|", actions.stream().map(RepairAction::actionId).toList());
            if (!replay.safe()) {
                if (targetSize == 1) {
                    RepairAction action = actions.getFirst();
                    state.rejected.putIfAbsent(action.actionId(), new RejectedRepairCandidate(
                            action.actionId(), action.type(), action.targetScheduleId(),
                            "REPLAY_DID_NOT_RESTORE_ALL_INVARIANTS",
                            "Deterministic replay left at least one protected invariant unresolved."));
                }
                return;
            }
            int disruption = actions.stream().mapToInt(RepairAction::disruptionScore).sum();
            BigDecimal money = money(actions.stream().map(RepairAction::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            RepairRankingFactors ranking = new RepairRankingFactors(true, actions.size(), disruption,
                    money, actions.stream().map(RepairAction::actionId).toList());
            String certificate = hash(ENGINE_VERSION + "|" + canonicalCashflows(baseCashflows)
                    + "|" + canonicalActions(actions) + "|" + canonicalForecast(replay)
                    + "|" + repairMaxCombinationSize + "|" + repairMaxEvaluatedCombinations);
            state.eligible.add(new RepairAlternative(0, "repair-" + certificate.substring(0, 16),
                    actions, replay, ranking, certificate, ""));
            return;
        }
        int remaining = targetSize - selected.size();
        for (int i = index; i <= candidates.size() - remaining && !state.capped; i++) {
            selected.add(candidates.get(i));
            evaluateRepairCombinations(candidates, targetSize, i + 1, selected, state,
                    startingBalance, protectedMinimum, horizonStart, horizonDays, baseCashflows, target);
            selected.remove(selected.size() - 1);
        }
    }

    private List<Cashflow> applyRepairs(
            List<Cashflow> baseline, List<RepairAction> actions, LocalDate horizonStart) {
        Map<String, Cashflow> flows = new LinkedHashMap<>();
        baseline.forEach(flow -> flows.put(flow.eventId(), flow));
        for (RepairAction action : actions) {
            switch (action.type()) {
                case "RESERVE_BUFFER" -> flows.put("repair:" + action.actionId(),
                        new Cashflow("repair:" + action.actionId(), horizonStart, action.amount(),
                                "REPAIR_PREVIEW", "Explicit reserve buffer", false, false,
                                null, false, false, null));
                case "SHIFT_OPTIONAL_SCHEDULE" -> action.affectedEventIds().forEach(id -> {
                    Cashflow source = flows.get(id);
                    if (source != null) flows.put(id, copy(source, action.effectiveDate(), source.amount()));
                });
                case "REDUCE_OPTIONAL_SCHEDULE" -> action.affectedEventIds().forEach(id -> {
                    Cashflow source = flows.get(id);
                    if (source != null) {
                        BigDecimal changed = source.amount().signum() < 0
                                ? source.amount().add(action.amount()).min(BigDecimal.ZERO)
                                : source.amount();
                        flows.put(id, copy(source, source.date(), changed));
                    }
                });
                case "TEMPORARY_SPENDING_LIMIT", "REVIEW_FLEXIBLE_EXPENSES" ->
                        reduceAffectedExpenses(flows, action.affectedEventIds(), action.amount());
                default -> throw new IllegalArgumentException("Unsupported repair action " + action.type());
            }
        }
        return flows.values().stream().sorted(cashflowOrder()).toList();
    }

    private void reduceAffectedExpenses(Map<String, Cashflow> flows, List<String> ids, BigDecimal amount) {
        BigDecimal remaining = amount;
        for (String id : ids.stream().sorted().toList()) {
            if (remaining.signum() <= 0) break;
            Cashflow source = flows.get(id);
            if (source == null || source.amount().signum() >= 0) continue;
            BigDecimal reduction = remaining.min(source.amount().abs());
            flows.put(id, copy(source, source.date(), source.amount().add(reduction)));
            remaining = money(remaining.subtract(reduction));
        }
    }

    private InvariantBreach floorBreach(
            LocalDate date, String eventId, String scheduleId,
            BigDecimal balance, BigDecimal protectedMinimum) {
        return new InvariantBreach("BALANCE_FLOOR_BREACH", date, eventId, scheduleId,
                money(balance), money(protectedMinimum),
                money(protectedMinimum.subtract(balance).max(BigDecimal.ZERO)),
                "Modeled available balance fell below the protected floor.");
    }

    private FailureProof baselineFailure(ForecastProof baseline) {
        return new FailureProof(true, true, 0, List.of(), baseline.failureDate(), baseline.lowestBalance(),
                baseline.triggeringEvents(), baseline.timeline(),
                "The baseline already fails at least one selected invariant, so the minimal failure set contains zero shocks.");
    }

    private Comparator<Cashflow> cashflowOrder() {
        return Comparator.comparing(Cashflow::date)
                .thenComparing(flow -> "REPAIR_PREVIEW".equals(flow.source()) ? 0 : 1)
                .thenComparing(Cashflow::source).thenComparing(Cashflow::eventId);
    }

    private Comparator<Candidate> candidateOrder() {
        return Comparator.comparing(Candidate::severity)
                .thenComparing(candidate -> candidate.forecast().failureDate(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(candidate -> candidate.shocks().stream()
                        .map(ShockInput::id).reduce("", (a, b) -> a + ":" + b));
    }

    private Comparator<RepairAlternative> repairAlternativeOrder() {
        return Comparator.comparingInt((RepairAlternative value) -> value.rankingFactors().actionCount())
                .thenComparingInt(value -> value.rankingFactors().disruptionScore())
                .thenComparing(value -> value.rankingFactors().moneyMovedOrDeferred())
                .thenComparing(value -> String.join("|", value.rankingFactors().stableActionIds()));
    }

    private Cashflow copy(Cashflow source, LocalDate date, BigDecimal amount) {
        return new Cashflow(source.eventId(), date, money(amount), source.source(), source.label(),
                source.flexible(), source.critical(), source.scheduleId(), source.protectedObligation(),
                source.repairEligible(), source.repairIneligibilityReason());
    }

    private TimelineEvent timelineEvent(Cashflow flow) {
        return new TimelineEvent(flow.eventId(), flow.date(), money(flow.amount()), flow.source(),
                flow.label(), flow.flexible(), flow.critical(), flow.scheduleId(), flow.protectedObligation());
    }

    private String canonicalCashflows(List<Cashflow> values) {
        return values.stream().sorted(cashflowOrder())
                .map(value -> "%s|%s|%s|%s|%s|%s|%s".formatted(
                        value.eventId(), value.date(), money(value.amount()).toPlainString(),
                        value.source(), value.scheduleId(), value.protectedObligation(), value.repairEligible()))
                .reduce("", (left, right) -> left + ";" + right);
    }

    private String canonicalActions(List<RepairAction> values) {
        return values.stream().sorted(Comparator.comparing(RepairAction::actionId))
                .map(value -> "%s|%s|%s|%s|%s".formatted(value.actionId(), value.type(),
                        money(value.amount()).toPlainString(), value.targetScheduleId(), value.effectiveDate()))
                .reduce("", (left, right) -> left + ";" + right);
    }

    private String canonicalForecast(ForecastProof value) {
        return "%s|%s|%s|%s|%s|%s".formatted(value.safe(), value.failureDate(),
                value.lowestBalance(), value.closingBalance(), value.balanceFloorSatisfied(),
                value.protectedObligationSatisfied());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private int requiredDays(ShockInput shock) {
        if (shock.days() == null || shock.days() < 1) {
            throw new IllegalArgumentException("Shock days must be positive");
        }
        return shock.days();
    }

    private BigDecimal requiredPercentage(ShockInput shock) {
        BigDecimal percentage = Objects.requireNonNull(
                shock.percentage(), "Income reduction percentage is required");
        if (percentage.signum() <= 0 || percentage.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("Shock percentage must be in (0, 100]");
        }
        return percentage;
    }

    private void requireMoney(BigDecimal value, String label) {
        if (value == null) throw new IllegalArgumentException(label + " is required");
    }

    private void requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private void requireNegative(BigDecimal value, String label) {
        if (value == null || value.signum() >= 0) {
            throw new IllegalArgumentException(label + " must be negative");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static final class SearchState {
        Candidate best;
        int evaluated;
        boolean capped;
    }

    private static final class RepairSearchState {
        final List<RepairAlternative> eligible = new ArrayList<>();
        final Map<String, RejectedRepairCandidate> rejected = new LinkedHashMap<>();
        int evaluated;
        boolean capped;
    }

    private record Applied(List<Cashflow> cashflows, List<AppliedShock> appliedShocks, BigDecimal severity) {}
    private record Candidate(List<ShockInput> shocks, List<AppliedShock> applied,
                             ForecastProof forecast, BigDecimal severity, List<Cashflow> cashflows) {}
    private record SearchOutcome(FailureProof failure, int evaluated, boolean capped,
                                 List<Cashflow> failureCashflows) {}
    private record CandidateCatalog(List<RepairAction> candidates,
                                    List<RejectedRepairCandidate> rejected) {}
}
