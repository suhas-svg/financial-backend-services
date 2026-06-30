package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.ScheduledTransferFrequency;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledTransferRecurrenceTest {

    @Test
    void weeklyRecurrenceAddsSevenDays() {
        Instant first = Instant.parse("2026-07-01T09:00:00Z");

        Instant next = ScheduledTransferService.nextRunAfter(first, ScheduledTransferFrequency.WEEKLY);

        assertThat(next).isEqualTo(Instant.parse("2026-07-08T09:00:00Z"));
    }

    @Test
    void biweeklyRecurrenceAddsFourteenDays() {
        Instant first = Instant.parse("2026-07-01T09:00:00Z");

        Instant next = ScheduledTransferService.nextRunAfter(first, ScheduledTransferFrequency.BIWEEKLY);

        assertThat(next).isEqualTo(Instant.parse("2026-07-15T09:00:00Z"));
    }

    @Test
    void monthlyRecurrenceUsesLastDayWhenTargetDayDoesNotExist() {
        Instant first = Instant.parse("2026-01-31T09:00:00Z");

        Instant next = ScheduledTransferService.nextRunAfter(first, ScheduledTransferFrequency.MONTHLY);

        assertThat(next).isEqualTo(Instant.parse("2026-02-28T09:00:00Z"));
    }
}
