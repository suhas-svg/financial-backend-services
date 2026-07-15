package com.suhasan.finance.transaction_service.outcome.service;

import com.suhasan.finance.transaction_service.entity.ScheduledTransfer;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferFrequency;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferType;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.ScenarioRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OutcomeScheduledTransferForecasterTest {
    private final OutcomeScheduledTransferForecaster forecaster = new OutcomeScheduledTransferForecaster();

    @Test
    void expandsOnlyActiveOwnedSameCurrencySchedulesInsideTheLocalHorizon() {
        ScenarioRequest request = request(LocalDate.of(2026, 7, 16), 15);
        ScheduledTransfer active = schedule("active", "customer-1", "10", "99", "2500.05", "INR",
                ScheduledTransferStatus.ACTIVE, ScheduledTransferType.RECURRING,
                ScheduledTransferFrequency.WEEKLY, "2026-07-15T18:30:00Z", null);

        List<ScheduledTransfer> schedules = List.of(
                active,
                schedule("paused", "customer-1", "10", "99", "99.00", "INR",
                        ScheduledTransferStatus.PAUSED, ScheduledTransferType.ONE_TIME, null,
                        "2026-07-16T06:00:00Z", null),
                schedule("other-owner", "customer-2", "10", "99", "99.00", "INR",
                        ScheduledTransferStatus.ACTIVE, ScheduledTransferType.ONE_TIME, null,
                        "2026-07-16T06:00:00Z", null),
                schedule("other-currency", "customer-1", "10", "99", "99.00", "USD",
                        ScheduledTransferStatus.ACTIVE, ScheduledTransferType.ONE_TIME, null,
                        "2026-07-16T06:00:00Z", null),
                schedule("net-zero", "customer-1", "10", "11", "99.00", "INR",
                        ScheduledTransferStatus.ACTIVE, ScheduledTransferType.ONE_TIME, null,
                        "2026-07-16T06:00:00Z", null));

        var events = forecaster.forecast(request, "customer-1", Set.of("10", "11"), schedules);

        assertThat(events).hasSize(3);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.scheduleId()).isEqualTo("active");
            assertThat(event.amount()).isEqualByComparingTo("-2500.05");
            assertThat(event.currency()).isEqualTo("INR");
            assertThat(event.status()).isEqualTo("ACTIVE");
            assertThat(event.cadence()).isEqualTo("WEEKLY");
            assertThat(event.evaluationTimeZone()).isEqualTo("Asia/Kolkata");
        });
        assertThat(events).extracting(event -> event.date().toString())
                .containsExactly("2026-07-16", "2026-07-23", "2026-07-30");
        assertThat(events.getFirst().scheduledFor()).isEqualTo(Instant.parse("2026-07-15T18:30:00Z"));
    }

    @Test
    void honorsInclusiveEndInstantAndMonthlyCadence() {
        ScenarioRequest request = request(LocalDate.of(2026, 7, 31), 32);
        ScheduledTransfer monthly = schedule("monthly", "customer-1", "99", "10", "1000.00", "INR",
                ScheduledTransferStatus.ACTIVE, ScheduledTransferType.RECURRING,
                ScheduledTransferFrequency.MONTHLY, "2026-07-31T03:30:00Z", "2026-08-31T03:30:00Z");

        var events = forecaster.forecast(request, "customer-1", Set.of("10"), List.of(monthly));

        assertThat(events).hasSize(2);
        assertThat(events).extracting(event -> event.date().toString())
                .containsExactly("2026-07-31", "2026-08-31");
        assertThat(events).allSatisfy(event -> assertThat(event.amount()).isEqualByComparingTo("1000.00"));
    }

    private ScenarioRequest request(LocalDate start, int days) {
        return new ScenarioRequest("INR protection", List.of("10"), "INR", "Asia/Kolkata",
                start, days, new BigDecimal("5000.00"), List.of(), List.of());
    }

    private ScheduledTransfer schedule(String id, String userId, String from, String to, String amount,
                                       String currency, ScheduledTransferStatus status,
                                       ScheduledTransferType type, ScheduledTransferFrequency frequency,
                                       String nextRunAt, String endAt) {
        return ScheduledTransfer.builder()
                .scheduleId(id).userId(userId).fromAccountId(from).toAccountId(to)
                .amount(new BigDecimal(amount)).currency(currency).description("Recurring obligation")
                .scheduleType(type).frequency(frequency).nextRunAt(Instant.parse(nextRunAt))
                .endAt(endAt == null ? null : Instant.parse(endAt)).status(status).build();
    }
}
