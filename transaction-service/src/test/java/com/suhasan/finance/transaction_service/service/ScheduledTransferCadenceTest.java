package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class ScheduledTransferCadenceTest {
    @Test
    void weeklyCadenceRetainsNineAmLocalAcrossSpringDst() {
        ScheduledTransfer schedule = schedule("America/New_York", LocalDateTime.parse("2026-03-01T09:00:00"),
                DstOverlapPolicy.EARLIER, DstGapPolicy.SHIFT_FORWARD, ScheduledTransferFrequency.WEEKLY);
        Instant first = ScheduledTransferCadence.resolve(schedule.getSourceLocalDateTime(), schedule.getSourceTimeZone(),
                schedule.getDstOverlapPolicy(), schedule.getDstGapPolicy());

        Instant second = ScheduledTransferCadence.nextRunAfter(schedule, first);
        Instant third = ScheduledTransferCadence.nextRunAfter(schedule, second);

        assertThat(first).isEqualTo(Instant.parse("2026-03-01T14:00:00Z"));
        assertThat(second).isEqualTo(Instant.parse("2026-03-08T13:00:00Z"));
        assertThat(third).isEqualTo(Instant.parse("2026-03-15T13:00:00Z"));
    }

    @Test
    void nonexistentLocalTimeUsesExplicitShiftOrRejectPolicy() {
        LocalDateTime missing = LocalDateTime.parse("2026-03-08T02:30:00");
        assertThat(ScheduledTransferCadence.resolve(missing, "America/New_York",
                DstOverlapPolicy.EARLIER, DstGapPolicy.SHIFT_FORWARD))
                .isEqualTo(Instant.parse("2026-03-08T07:30:00Z"));
        assertThatThrownBy(() -> ScheduledTransferCadence.resolve(missing, "America/New_York",
                DstOverlapPolicy.EARLIER, DstGapPolicy.REJECT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not exist");
    }

    @Test
    void ambiguousLocalTimeUsesDeterministicEarlierOrLaterOffset() {
        LocalDateTime repeated = LocalDateTime.parse("2026-11-01T01:30:00");
        assertThat(ScheduledTransferCadence.resolve(repeated, "America/New_York",
                DstOverlapPolicy.EARLIER, DstGapPolicy.SHIFT_FORWARD))
                .isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
        assertThat(ScheduledTransferCadence.resolve(repeated, "America/New_York",
                DstOverlapPolicy.LATER, DstGapPolicy.SHIFT_FORWARD))
                .isEqualTo(Instant.parse("2026-11-01T06:30:00Z"));
    }

    @Test
    void endOfMonthAnchorDoesNotDriftAfterFebruary() {
        ScheduledTransfer schedule = schedule("UTC", LocalDateTime.parse("2026-01-31T09:00:00"),
                DstOverlapPolicy.EARLIER, DstGapPolicy.SHIFT_FORWARD, ScheduledTransferFrequency.MONTHLY);
        Instant january = Instant.parse("2026-01-31T09:00:00Z");
        Instant february = ScheduledTransferCadence.nextRunAfter(schedule, january);
        Instant march = ScheduledTransferCadence.nextRunAfter(schedule, february);
        assertThat(february).isEqualTo(Instant.parse("2026-02-28T09:00:00Z"));
        assertThat(march).isEqualTo(Instant.parse("2026-03-31T09:00:00Z"));
    }

    private ScheduledTransfer schedule(String zone, LocalDateTime local, DstOverlapPolicy overlap,
                                       DstGapPolicy gap, ScheduledTransferFrequency frequency) {
        return ScheduledTransfer.builder().sourceTimeZone(zone).sourceLocalDateTime(local)
                .dstOverlapPolicy(overlap).dstGapPolicy(gap).frequency(frequency)
                .recurrenceAnchorDay(local.getDayOfMonth())
                .recurrenceAnchorEndOfMonth(local.getDayOfMonth() == local.toLocalDate().lengthOfMonth())
                .build();
    }
}
