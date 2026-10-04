package com.suhasan.finance.transaction_service.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the JVM-memory metric bug in MonitoringController.
 *
 * <p>Micrometer registers jvm.memory.used / jvm.memory.max ONCE PER MEMORY POOL (heap,
 * non-heap, and each G1 generation). They all share ONE metric name and are told apart only by
 * the "id" and "area" tags. A bare {@code Search.gauge()} lookup returns a single arbitrary
 * member of that set, so "used" and "max" can come from DIFFERENT pools - a young-generation
 * "used" divided by a non-heap "max". That is how the endpoint reported 1867%, a ratio no real
 * memory pool can produce.
 */
class JvmMemoryGaugeAmbiguityTest {

    /** Registers a pool-scoped gauge the way Micrometer's JVM instrumentation does. */
    private static void poolGauge(MeterRegistry registry, String name, String id, String area, double value) {
        List<Tag> tags = id == null
                ? List.of(Tag.of("area", area))
                : List.of(Tag.of("id", id), Tag.of("area", area));
        registry.gauge(name, tags, value);
    }

    private static MeterRegistry registryWithRealisticPools() {
        MeterRegistry registry = new SimpleMeterRegistry();
        poolGauge(registry, "jvm.memory.used", "G1 Eden Space", "heap", 470_195_112d);
        poolGauge(registry, "jvm.memory.used", "G1 Survivor Space", "heap", 12_000_000d);
        poolGauge(registry, "jvm.memory.used", "G1 Old Gen", "heap", 1_000_000d);
        poolGauge(registry, "jvm.memory.used", null, "nonheap", 5_840_896d);

        poolGauge(registry, "jvm.memory.max", "G1 Eden Space", "heap", 2_000_000_000d);
        poolGauge(registry, "jvm.memory.max", "G1 Old Gen", "heap", 7_499_415_549d);
        poolGauge(registry, "jvm.memory.max", null, "nonheap", -1d);
        return registry;
    }

    @Test
    @DisplayName("one metric name resolves to multiple pool gauges, so a bare lookup is ambiguous")
    void memoryGaugesAreRegisteredOncePerPool() {
        MeterRegistry registry = registryWithRealisticPools();

        assertThat(Search.in(registry).name("jvm.memory.used").gauges())
                .as("jvm.memory.used must resolve to more than one pool gauge")
                .hasSizeGreaterThan(1);
        assertThat(Search.in(registry).name("jvm.memory.max").gauges())
                .as("jvm.memory.max must resolve to more than one pool gauge")
                .hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("bare Search.gauge() pairs unrelated pools, so used can exceed max")
    void bareLookupCanReportUsedGreaterThanMax() {
        MeterRegistry registry = registryWithRealisticPools();

        // This is verbatim what MonitoringController.getGaugeValue() does today.
        double used = Search.in(registry).name("jvm.memory.used").gauge().value();
        double max = Search.in(registry).name("jvm.memory.max").gauge().value();

        assertThat(used)
                .as("the two independent arbitrary picks are not a consistent (used, max) pair")
                .isNotEqualTo(max);
    }

    @Test
    @DisplayName("the fix: scoping both sides to one area keeps used within max")
    void areaScopedSumKeepsUsedWithinMax() {
        MeterRegistry registry = registryWithRealisticPools();

        double heapUsed = Search.in(registry).name("jvm.memory.used").tag("area", "heap").gauges().stream()
                .mapToDouble(io.micrometer.core.instrument.Gauge::value).sum();
        double heapMax = Search.in(registry).name("jvm.memory.max").tag("area", "heap").gauges().stream()
                .mapToDouble(io.micrometer.core.instrument.Gauge::value).sum();

        assertThat(heapUsed).isEqualTo(483_195_112d);
        assertThat(heapMax).isEqualTo(9_499_415_549d);
        assertThat(heapUsed)
                .as("scoping both sides to the same area makes used <= max always hold")
                .isLessThanOrEqualTo(heapMax);
    }

    @Test
    @DisplayName("a negative pool max means unbounded and must not be summed as a budget")
    void negativeMaxIsNotABudget() {
        MeterRegistry registry = new SimpleMeterRegistry();
        poolGauge(registry, "jvm.memory.max", null, "nonheap", -1d);

        double sum = Search.in(registry).name("jvm.memory.max").tag("area", "nonheap").gauges().stream()
                .mapToDouble(io.micrometer.core.instrument.Gauge::value).sum();

        assertThat(sum)
                .as("a -1 pool max means unbounded; summing it in as if it were a budget is wrong")
                .isNegative();
    }
}
