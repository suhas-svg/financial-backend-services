package com.suhasan.finance.transaction_service.outcome.service;

import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutcomeSimulationEngineTest {
    private final OutcomeSimulationEngine engine = new OutcomeSimulationEngine(3, 100);
    private final LocalDate start = LocalDate.of(2026, 7, 15);

    @Test
    void findsExactOneShockFailureAndCompilesVerifiedRepair() {
        List<OutcomeSimulationEngine.Cashflow> cashflows = List.of(
                flow("salary", 2, "5000.00", "Salary", false, true),
                flow("rent", 5, "-12000.00", "Rent", false, true));
        ShockInput delay = new ShockInput("delay-salary", ShockType.INCOME_DELAY, "salary", 5,
                null, null, "Salary arrives five days late");

        SimulationProof result = engine.simulate(money("20000"), money("10000"), start, 10, cashflows, List.of(delay));

        assertThat(result.baseline().safe()).isTrue();
        assertThat(result.baseline().lowestBalance()).isEqualByComparingTo("13000.00");
        assertThat(result.reverseStress().failureFound()).isTrue();
        assertThat(result.reverseStress().minimalShockCount()).isEqualTo(1);
        assertThat(result.reverseStress().failureDate()).isEqualTo(start.plusDays(5));
        assertThat(result.reverseStress().lowestBalance()).isEqualByComparingTo("8000.00");
        assertThat(result.reverseStress().triggeringEvents()).extracting(TimelineEvent::eventId).containsExactly("rent");
        assertThat(result.repair().maximumShortfall()).isEqualByComparingTo("2000.00");
        assertThat(result.repair().selectedRepairs()).singleElement().satisfies(repair -> {
            assertThat(repair.type()).isEqualTo("RESERVE_BUFFER");
            assertThat(repair.amount()).isEqualByComparingTo("2000.00");
        });
        assertThat(result.repair().verifiedInModel()).isTrue();
    }

    @Test
    void provesNoSingleShockFailsBeforeSelectingTwoShockSet() {
        List<OutcomeSimulationEngine.Cashflow> cashflows = List.of(
                flow("salary", 2, "5000.00", "Salary", false, true),
                flow("bill", 5, "-9000.00", "Critical bill", false, true));
        List<ShockInput> shocks = List.of(
                new ShockInput("reduce-income", ShockType.INCOME_REDUCTION, "salary", null,
                        null, new BigDecimal("20"), "Income is reduced by 20%"),
                new ShockInput("spike-bill", ShockType.EXPENSE_SPIKE, "bill", null,
                        new BigDecimal("1000"), null, "Bill is 1000 higher"));

        SimulationProof result = engine.simulate(money("15000"), money("10000"), start, 10, cashflows, shocks);

        assertThat(result.reverseStress().minimalShockCount()).isEqualTo(2);
        assertThat(result.reverseStress().appliedShocks()).extracting(AppliedShock::shockId)
                .containsExactly("reduce-income", "spike-bill");
        assertThat(result.evaluatedCombinations()).isEqualTo(3);
        assertThat(result.searchCapped()).isFalse();
        assertThat(result.reverseStress().minimalityExplanation()).contains("fewer than 2 shocks");
    }

    @Test
    void reportsBoundedSearchWhenCombinationCapIsReached() {
        OutcomeSimulationEngine capped = new OutcomeSimulationEngine(3, 1);
        List<OutcomeSimulationEngine.Cashflow> cashflows = List.of(
                flow("income", 2, "1000.00", "Income", false, true),
                flow("expense", 5, "-1000.00", "Expense", true, false));
        List<ShockInput> shocks = List.of(
                new ShockInput("a", ShockType.INCOME_DELAY, "income", 1, null, null, "Small delay"),
                new ShockInput("b", ShockType.EXPENSE_SPIKE, "expense", null, money("1"), null, "Small spike"));

        SimulationProof result = capped.simulate(money("20000"), money("10000"), start, 10, cashflows, shocks);

        assertThat(result.searchCapped()).isTrue();
        assertThat(result.evaluatedCombinations()).isEqualTo(1);
        assertThat(result.reverseStress().failureFound()).isFalse();
        assertThat(result.reverseStress().minimalityExplanation()).contains("configured combination cap");
    }

    private OutcomeSimulationEngine.Cashflow flow(String id, int day, String amount, String label,
                                                    boolean flexible, boolean critical) {
        return new OutcomeSimulationEngine.Cashflow(id, start.plusDays(day), money(amount),
                "ASSUMPTION", label, flexible, critical);
    }

    private BigDecimal money(String value) { return new BigDecimal(value).setScale(2); }
}
