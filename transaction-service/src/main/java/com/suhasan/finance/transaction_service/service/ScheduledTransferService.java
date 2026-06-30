package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.ScheduledTransferFrequency;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

public final class ScheduledTransferService {

    private ScheduledTransferService() {
    }

    public static Instant nextRunAfter(Instant runAt, ScheduledTransferFrequency frequency) {
        Objects.requireNonNull(runAt, "runAt must not be null");
        Objects.requireNonNull(frequency, "frequency must not be null");

        return switch (frequency) {
            case WEEKLY -> runAt.plusSeconds(7L * 24 * 60 * 60);
            case BIWEEKLY -> runAt.plusSeconds(14L * 24 * 60 * 60);
            case MONTHLY -> ZonedDateTime.ofInstant(runAt, ZoneOffset.UTC)
                    .plusMonths(1)
                    .toInstant();
        };
    }
}
