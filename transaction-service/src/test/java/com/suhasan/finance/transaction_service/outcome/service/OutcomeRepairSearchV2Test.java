package com.suhasan.finance.transaction_service.outcome.service;

import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutcomeRepairSearchV2Test {
    private static final LocalDate START = LocalDate.of(2026, 7, 17);

    @Test
    void explainsProtectedObligationFailureSeparatelyFromBalanceFloor() {
        OutcomeSimulationEngine engine = new OutcomeSimulationEngine(3, 100, 3, 100);
        var obligation = schedule("rent-occurrence", "rent", 2, "-700.00", true, false);

        SimulationProof result = engine.simulate(money("500"), money("0"), START, 7,
                List.of(obligation), List.of(),
                new OutcomeSimulationEngine.ProtectionTarget(
                        OutcomeType.SCHEDULED_OBLIGATION, "rent", true, null));

        assertThat(result.baseline().safe()).isFalse();
        assertThat(result.baseline().protectedObligationSatisfied()).isFalse();
        assertThat(result.baseline().invariantBreaches())
                .extracting(InvariantBreach::type)
                .contains("PROTECTED_OBLIGATION_INSUFFICIENT_FUNDS");
        assertThat(result.baseline().invariantBreaches())
                .filteredOn(breach -> breach.type().equals("PROTECTED_OBLIGATION_INSUFFICIENT_FUNDS"))
                .singleElement()
                .extracting(InvariantBreach::shortfall)
                .isEqualTo(money("200"));
    }

    @Test
    void ranksSeveralReplayProvenRepairTypesDeterministically() {
        OutcomeSimulationEngine engine = new OutcomeSimulationEngine(3, 100, 3, 500);
        List<OutcomeSimulationEngine.Cashflow> cashflows = List.of(
                new OutcomeSimulationEngine.Cashflow("flex", START.plusDays(1), money("-200"),
                        "ASSUMPTION", "Flexible shopping", true, false),
                schedule("optional-occurrence", "optional", 1, "-100", false, true),
                schedule("rent-occurrence", "rent", 2, "-900", true, false),
                new OutcomeSimulationEngine.Cashflow("income", START.plusDays(3), money("500"),
                        "ASSUMPTION", "Income", false, true));
        var target = new OutcomeSimulationEngine.ProtectionTarget(
                OutcomeType.SCHEDULED_OBLIGATION, "rent", true, null);

        SimulationProof first = engine.simulate(money("1100"), money("0"), START, 7,
                cashflows, List.of(), target);
        SimulationProof second = engine.simulate(money("1100"), money("0"), START, 7,
                cashflows, List.of(), target);

        assertThat(first.repair().alternatives()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(first.repair().alternatives().stream()
                .flatMap(alternative -> alternative.actions().stream())
                .map(RepairAction::type))
                .contains("RESERVE_BUFFER", "SHIFT_OPTIONAL_SCHEDULE",
                        "REDUCE_OPTIONAL_SCHEDULE", "TEMPORARY_SPENDING_LIMIT",
                        "REVIEW_FLEXIBLE_EXPENSES");
        assertThat(first.repair().alternatives())
                .allSatisfy(alternative -> {
                    assertThat(alternative.replay().safe()).isTrue();
                    assertThat(alternative.rankingFactors().restoresAllInvariants()).isTrue();
                    assertThat(alternative.certificateHash()).matches("[a-f0-9]{64}");
                });
        assertThat(first.repair().alternatives())
                .extracting(RepairAlternative::alternativeId)
                .isEqualTo(second.repair().alternatives()
                        .stream().map(RepairAlternative::alternativeId).toList());
        assertThat(first.repair().certificateHash()).isEqualTo(second.repair().certificateHash());
        assertThat(first.repair().alternatives())
                .anySatisfy(alternative -> assertThat(alternative.actions()).hasSizeGreaterThan(1));
    }

    @Test
    void reportsRepairSearchCapWithoutClaimingGlobalOptimality() {
        OutcomeSimulationEngine engine = new OutcomeSimulationEngine(3, 100, 3, 1);
        List<OutcomeSimulationEngine.Cashflow> cashflows = List.of(
                new OutcomeSimulationEngine.Cashflow("flex", START.plusDays(1), money("-200"),
                        "ASSUMPTION", "Flexible shopping", true, false),
                schedule("rent-occurrence", "rent", 2, "-900", true, false));

        SimulationProof result = engine.simulate(money("900"), money("0"), START, 7,
                cashflows, List.of(),
                new OutcomeSimulationEngine.ProtectionTarget(
                        OutcomeType.SCHEDULED_OBLIGATION, "rent", true, null));

        assertThat(result.repair().searchCapped()).isTrue();
        assertThat(result.repair().evaluatedCombinations()).isEqualTo(1);
        assertThat(result.repair().minimalityExplanation()).contains("configured bounded search");
        assertThat(result.repair().minimalityExplanation()).contains("not a claim of global optimality");
    }

    @Test
    void excludesProtectedAndIneligibleSchedulesAndDoesNotMutateInputs() {
        OutcomeSimulationEngine engine = new OutcomeSimulationEngine(3, 100, 3, 100);
        var protectedFlow = schedule("rent-occurrence", "rent", 2, "-900", true, false);
        var immutableFlow = schedule("tax-occurrence", "tax", 1, "-200", false, false);
        List<OutcomeSimulationEngine.Cashflow> inputs = List.of(protectedFlow, immutableFlow);

        SimulationProof result = engine.simulate(money("700"), money("0"), START, 7,
                inputs, List.of(),
                new OutcomeSimulationEngine.ProtectionTarget(
                        OutcomeType.SCHEDULED_OBLIGATION, "rent", true, null));

        assertThat(result.repair().rejectedCandidates())
                .extracting(RejectedRepairCandidate::reasonCode)
                .contains("PROTECTED_OBLIGATION", "SCHEDULE_INELIGIBLE");
        assertThat(inputs).containsExactly(protectedFlow, immutableFlow);
        assertThat(inputs).allSatisfy(flow -> assertThat(flow.date()).isBefore(START.plusDays(3)));
    }

    @Test
    void staleProtectedObligationSnapshotFailsClosed() {
        OutcomeSimulationEngine engine = new OutcomeSimulationEngine(3, 100, 3, 100);

        SimulationProof result = engine.simulate(money("2000"), money("0"), START, 7,
                List.of(), List.of(),
                new OutcomeSimulationEngine.ProtectionTarget(
                        OutcomeType.SCHEDULED_OBLIGATION, "rent", false,
                        "The protected obligation version changed after selection."));

        assertThat(result.baseline().safe()).isFalse();
        assertThat(result.baseline().invariantBreaches())
                .singleElement()
                .satisfies(breach -> {
                    assertThat(breach.type()).isEqualTo("PROTECTED_OBLIGATION_MISSING_OR_CHANGED");
                    assertThat(breach.explanation()).contains("version changed");
                });
    }
    private OutcomeSimulationEngine.Cashflow schedule(
            String eventId, String scheduleId, int day, String amount,
            boolean protectedObligation, boolean eligible) {
        return new OutcomeSimulationEngine.Cashflow(eventId, START.plusDays(day), money(amount),
                "SCHEDULED_TRANSFER", scheduleId, eligible, protectedObligation,
                scheduleId, protectedObligation, eligible,
                eligible ? null : "Schedule is critical or immutable");
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
