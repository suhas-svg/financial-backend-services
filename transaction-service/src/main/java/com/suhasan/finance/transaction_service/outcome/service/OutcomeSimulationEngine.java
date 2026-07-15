package com.suhasan.finance.transaction_service.outcome.service;

import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Component
public class OutcomeSimulationEngine {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final int maxCombinationSize;
    private final int maxEvaluatedCombinations;

    public OutcomeSimulationEngine(
            @Value("${outcome-protection.search.max-combination-size:3}") int maxCombinationSize,
            @Value("${outcome-protection.search.max-evaluated-combinations:5000}") int maxEvaluatedCombinations) {
        this.maxCombinationSize = Math.max(1, Math.min(maxCombinationSize, 5));
        this.maxEvaluatedCombinations = Math.max(1, Math.min(maxEvaluatedCombinations, 100000));
    }

    public record Cashflow(String eventId, LocalDate date, BigDecimal amount, String source,
                           String label, boolean flexible, boolean critical) {}

    public SimulationProof simulate(BigDecimal startingBalance, BigDecimal protectedMinimum,
                                    LocalDate horizonStart, int horizonDays,
                                    List<Cashflow> cashflows, List<ShockInput> shocks) {
        requireMoney(startingBalance, "starting balance");
        requireMoney(protectedMinimum, "protected minimum");
        if (horizonDays < 1 || horizonDays > 90) throw new IllegalArgumentException("Horizon must be between 1 and 90 days");

        List<Cashflow> ordered = cashflows.stream().sorted(cashflowOrder()).toList();
        ForecastProof baseline = forecast(startingBalance, protectedMinimum, horizonStart, horizonDays, ordered);
        SearchOutcome search = baseline.safe()
                ? searchFailure(startingBalance, protectedMinimum, horizonStart, horizonDays, ordered, shocks)
                : new SearchOutcome(baselineFailure(baseline), 0, false);
        ForecastProof repairTarget = search.failure().failureFound()
                ? new ForecastProof(false, startingBalance, protectedMinimum, search.failure().failureDate(),
                    search.failure().lowestBalance(),
                    search.failure().timeline().isEmpty() ? startingBalance : search.failure().timeline().get(search.failure().timeline().size() - 1).closingBalance(),
                    search.failure().triggeringEvents(), search.failure().timeline())
                : baseline;
        RepairPlan repair = compileRepair(protectedMinimum, repairTarget, ordered);
        return new SimulationProof(baseline, search.failure(), repair, search.evaluated(), search.capped());
    }

    private SearchOutcome searchFailure(BigDecimal startingBalance, BigDecimal protectedMinimum,
                                        LocalDate horizonStart, int horizonDays,
                                        List<Cashflow> baselineCashflows, List<ShockInput> shocks) {
        List<ShockInput> orderedShocks = shocks == null ? List.of() : shocks.stream()
                .sorted(Comparator.comparing(ShockInput::id)).toList();
        SearchState state = new SearchState();
        int maxSize = Math.min(maxCombinationSize, orderedShocks.size());
        for (int size = 1; size <= maxSize && !state.capped; size++) {
            evaluateCombinations(orderedShocks, size, 0, new ArrayList<>(), state,
                    startingBalance, protectedMinimum, horizonStart, horizonDays, baselineCashflows);
            if (state.best != null) break;
        }
        if (state.best == null) {
            String explanation = state.capped
                    ? "No failure was found before the configured combination cap was reached."
                    : "No submitted shock combination up to size %d caused the protected outcome to fail."
                        .formatted(maxSize);
            return new SearchOutcome(new FailureProof(false, false, null, List.of(), null,
                    null, List.of(), List.of(), explanation), state.evaluated, state.capped);
        }
        Candidate best = state.best;
        String explanation = state.capped
                ? "A %d-shock failure was found in the bounded search; the evaluation cap was reached while comparing same-size candidates."
                    .formatted(best.shocks.size())
                : "No combination with fewer than %d shocks failed; this is the lowest-severity failing set among all evaluated %d-shock combinations."
                    .formatted(best.shocks.size(), best.shocks.size());
        FailureProof proof = new FailureProof(true, false, best.shocks.size(), best.applied,
                best.forecast.failureDate(), best.forecast.lowestBalance(), best.forecast.triggeringEvents(),
                best.forecast.timeline(), explanation);
        return new SearchOutcome(proof, state.evaluated, state.capped);
    }

    private void evaluateCombinations(List<ShockInput> shocks, int targetSize, int index,
                                      List<ShockInput> selected, SearchState state,
                                      BigDecimal startingBalance, BigDecimal protectedMinimum,
                                      LocalDate horizonStart, int horizonDays, List<Cashflow> baselineCashflows) {
        if (state.capped) return;
        if (selected.size() == targetSize) {
            if (state.evaluated >= maxEvaluatedCombinations) { state.capped = true; return; }
            state.evaluated++;
            Applied applied = applyShocks(baselineCashflows, selected, horizonDays);
            ForecastProof forecast = forecast(startingBalance, protectedMinimum, horizonStart, horizonDays, applied.cashflows);
            if (!forecast.safe()) {
                Candidate candidate = new Candidate(List.copyOf(selected), applied.appliedShocks, forecast, applied.severity);
                if (state.best == null || candidateOrder().compare(candidate, state.best) < 0) state.best = candidate;
            }
            return;
        }
        int remaining = targetSize - selected.size();
        for (int i = index; i <= shocks.size() - remaining && !state.capped; i++) {
            selected.add(shocks.get(i));
            evaluateCombinations(shocks, targetSize, i + 1, selected, state,
                    startingBalance, protectedMinimum, horizonStart, horizonDays, baselineCashflows);
            selected.remove(selected.size() - 1);
        }
    }

    private Applied applyShocks(List<Cashflow> baseline, List<ShockInput> selected, int horizonDays) {
        Map<String, Cashflow> cashflows = new LinkedHashMap<>();
        baseline.forEach(flow -> cashflows.put(flow.eventId(), flow));
        List<AppliedShock> applied = new ArrayList<>();
        BigDecimal totalSeverity = BigDecimal.ZERO;

        for (ShockInput shock : selected.stream().sorted(Comparator.comparing(ShockInput::id)).toList()) {
            Cashflow target = cashflows.get(shock.targetAssumptionId());
            if (target == null || !"ASSUMPTION".equals(target.source())) {
                throw new IllegalArgumentException("Shock %s targets an unknown assumption".formatted(shock.id()));
            }
            Cashflow changed;
            BigDecimal severity;
            switch (shock.type()) {
                case INCOME_DELAY -> {
                    requirePositive(target.amount(), "Income delay target");
                    int days = requiredDays(shock);
                    changed = copy(target, target.date().plusDays(days), target.amount());
                    severity = target.amount().multiply(BigDecimal.valueOf(days))
                            .divide(BigDecimal.valueOf(horizonDays), 2, RoundingMode.HALF_UP);
                }
                case INCOME_REDUCTION -> {
                    requirePositive(target.amount(), "Income reduction target");
                    BigDecimal percentage = requiredPercentage(shock);
                    BigDecimal reduction = money(target.amount().multiply(percentage).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
                    changed = copy(target, target.date(), target.amount().subtract(reduction));
                    severity = reduction;
                }
                case EXPENSE_SPIKE -> {
                    requireNegative(target.amount(), "Expense spike target");
                    BigDecimal amount = money(Objects.requireNonNull(shock.amount(), "Expense spike amount is required"));
                    requirePositive(amount, "Expense spike amount");
                    changed = copy(target, target.date(), target.amount().subtract(amount));
                    severity = amount;
                }
                case PAYMENT_TIMING_SHIFT -> {
                    requireNegative(target.amount(), "Payment timing target");
                    int days = requiredDays(shock);
                    changed = copy(target, target.date().minusDays(days), target.amount());
                    severity = target.amount().abs().multiply(BigDecimal.valueOf(days))
                            .divide(BigDecimal.valueOf(horizonDays), 2, RoundingMode.HALF_UP);
                }
                default -> throw new IllegalArgumentException("Unsupported shock type");
            }
            cashflows.put(target.eventId(), changed);
            totalSeverity = totalSeverity.add(severity);
            applied.add(new AppliedShock(shock.id(), shock.type(), shock.label(), shock.targetAssumptionId(), money(severity)));
        }
        return new Applied(cashflows.values().stream().sorted(cashflowOrder()).toList(), applied, money(totalSeverity));
    }

    private ForecastProof forecast(BigDecimal startingBalance, BigDecimal protectedMinimum,
                                   LocalDate horizonStart, int horizonDays, List<Cashflow> cashflows) {
        LocalDate horizonEnd = horizonStart.plusDays(horizonDays - 1L);
        Map<LocalDate, List<Cashflow>> byDate = new HashMap<>();
        cashflows.stream().filter(flow -> !flow.date().isBefore(horizonStart) && !flow.date().isAfter(horizonEnd))
                .forEach(flow -> byDate.computeIfAbsent(flow.date(), ignored -> new ArrayList<>()).add(flow));

        BigDecimal balance = money(startingBalance);
        BigDecimal lowest = balance;
        LocalDate failureDate = balance.compareTo(protectedMinimum) < 0 ? horizonStart : null;
        List<TimelineEvent> triggers = List.of();
        List<TimelineDay> timeline = new ArrayList<>(horizonDays);
        for (int day = 0; day < horizonDays; day++) {
            LocalDate date = horizonStart.plusDays(day);
            BigDecimal opening = balance;
            List<TimelineEvent> events = byDate.getOrDefault(date, List.of()).stream()
                    .sorted(cashflowOrder()).map(this::timelineEvent).toList();
            for (TimelineEvent event : events) balance = money(balance.add(event.amount()));
            if (balance.compareTo(lowest) < 0) lowest = balance;
            if (failureDate == null && balance.compareTo(protectedMinimum) < 0) {
                failureDate = date;
                triggers = events;
            }
            timeline.add(new TimelineDay(date, opening, events, balance));
        }
        return new ForecastProof(failureDate == null, money(startingBalance), money(protectedMinimum), failureDate,
                money(lowest), money(balance), triggers, timeline);
    }

    private RepairPlan compileRepair(BigDecimal protectedMinimum, ForecastProof target, List<Cashflow> baseline) {
        if (target.safe()) {
            return new RepairPlan(BigDecimal.ZERO.setScale(2), List.of(), true,
                    "The protected outcome is already satisfied; no repair action is required.");
        }
        BigDecimal shortfall = money(protectedMinimum.subtract(target.lowestBalance()).max(BigDecimal.ZERO));
        RepairAction reserve = new RepairAction("reserve-buffer", "RESERVE_BUFFER", shortfall, List.of(),
                "Keep an additional %s available through the failure date; this exactly covers the modeled maximum shortfall."
                        .formatted(shortfall.toPlainString()));

        List<Cashflow> flexibleExpenses = baseline.stream()
                .filter(flow -> flow.flexible() && !flow.critical() && flow.amount().signum() < 0)
                .sorted(Comparator.comparing((Cashflow flow) -> flow.amount().abs()).thenComparing(Cashflow::eventId))
                .toList();
        List<String> deferred = smallestExpenseSet(flexibleExpenses, shortfall);
        if (!deferred.isEmpty()) {
            BigDecimal impact = flexibleExpenses.stream().filter(flow -> deferred.contains(flow.eventId()))
                    .map(flow -> flow.amount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
            RepairAction review = new RepairAction("review-flexible-expenses", "REVIEW_FLEXIBLE_EXPENSES",
                    money(impact), deferred, "Review or defer the listed flexible, non-critical assumptions; their modeled value covers the shortfall.");
            return new RepairPlan(shortfall, List.of(review), true,
                    "One advisory action restores the outcome; the selected flexible-expense review avoids changing critical obligations.");
        }
        return new RepairPlan(shortfall, List.of(reserve), true,
                "One advisory reserve-buffer action restores the modeled outcome and is minimal by action count.");
    }

    private List<String> smallestExpenseSet(List<Cashflow> expenses, BigDecimal required) {
        for (int size = 1; size <= expenses.size(); size++) {
            List<String> found = findExpenseSet(expenses, required, size, 0, new ArrayList<>());
            if (!found.isEmpty()) return found;
        }
        return List.of();
    }

    private List<String> findExpenseSet(List<Cashflow> expenses, BigDecimal required, int size,
                                        int index, List<Cashflow> selected) {
        if (selected.size() == size) {
            BigDecimal sum = selected.stream().map(flow -> flow.amount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.compareTo(required) >= 0 ? selected.stream().map(Cashflow::eventId).toList() : List.of();
        }
        int remaining = size - selected.size();
        for (int i = index; i <= expenses.size() - remaining; i++) {
            selected.add(expenses.get(i));
            List<String> found = findExpenseSet(expenses, required, size, i + 1, selected);
            selected.remove(selected.size() - 1);
            if (!found.isEmpty()) return found;
        }
        return List.of();
    }

    private FailureProof baselineFailure(ForecastProof baseline) {
        return new FailureProof(true, true, 0, List.of(), baseline.failureDate(), baseline.lowestBalance(),
                baseline.triggeringEvents(), baseline.timeline(),
                "The baseline already fails, so the minimal failure set contains zero shocks.");
    }

    private Comparator<Cashflow> cashflowOrder() {
        return Comparator.comparing(Cashflow::date).thenComparing(Cashflow::source).thenComparing(Cashflow::eventId);
    }

    private Comparator<Candidate> candidateOrder() {
        return Comparator.comparing((Candidate candidate) -> candidate.severity)
                .thenComparing(candidate -> candidate.forecast.failureDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(candidate -> candidate.shocks.stream().map(ShockInput::id).reduce("", (a, b) -> a + ":" + b));
    }

    private Cashflow copy(Cashflow source, LocalDate date, BigDecimal amount) {
        return new Cashflow(source.eventId(), date, money(amount), source.source(), source.label(), source.flexible(), source.critical());
    }

    private TimelineEvent timelineEvent(Cashflow flow) {
        return new TimelineEvent(flow.eventId(), flow.date(), money(flow.amount()), flow.source(), flow.label(), flow.flexible(), flow.critical());
    }

    private int requiredDays(ShockInput shock) {
        if (shock.days() == null || shock.days() < 1) throw new IllegalArgumentException("Shock days must be positive");
        return shock.days();
    }

    private BigDecimal requiredPercentage(ShockInput shock) {
        BigDecimal percentage = Objects.requireNonNull(shock.percentage(), "Income reduction percentage is required");
        if (percentage.signum() <= 0 || percentage.compareTo(ONE_HUNDRED) > 0) throw new IllegalArgumentException("Shock percentage must be in (0, 100]");
        return percentage;
    }

    private void requireMoney(BigDecimal value, String label) {
        if (value == null) throw new IllegalArgumentException(label + " is required");
    }
    private void requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(label + " must be positive");
    }
    private void requireNegative(BigDecimal value, String label) {
        if (value == null || value.signum() >= 0) throw new IllegalArgumentException(label + " must be negative");
    }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private static final class SearchState { Candidate best; int evaluated; boolean capped; }
    private record Applied(List<Cashflow> cashflows, List<AppliedShock> appliedShocks, BigDecimal severity) {}
    private record Candidate(List<ShockInput> shocks, List<AppliedShock> applied, ForecastProof forecast, BigDecimal severity) {}
    private record SearchOutcome(FailureProof failure, int evaluated, boolean capped) {}
}
