package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.DstGapPolicy;
import com.suhasan.finance.transaction_service.entity.DstOverlapPolicy;
import com.suhasan.finance.transaction_service.entity.ScheduledTransfer;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferFrequency;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Objects;

/** Shared recurrence boundary used by execution and Outcome Protection forecasting. */
public final class ScheduledTransferCadence {
    private ScheduledTransferCadence() {}

    public static Instant resolve(LocalDateTime localDateTime, String timeZone,
                                  DstOverlapPolicy overlapPolicy, DstGapPolicy gapPolicy) {
        Objects.requireNonNull(localDateTime, "Local schedule time is required");
        ZoneId zone = ZoneId.of(Objects.requireNonNull(timeZone, "Schedule time zone is required"));
        DstOverlapPolicy overlap = overlapPolicy == null ? DstOverlapPolicy.EARLIER : overlapPolicy;
        DstGapPolicy gap = gapPolicy == null ? DstGapPolicy.SHIFT_FORWARD : gapPolicy;
        ZoneRules rules = zone.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(localDateTime);
        if (offsets.size() == 1) {
            return localDateTime.atOffset(offsets.getFirst()).toInstant();
        }
        if (offsets.size() == 2) {
            ZoneOffset selected = overlap == DstOverlapPolicy.EARLIER ? offsets.getFirst() : offsets.getLast();
            return localDateTime.atOffset(selected).toInstant();
        }
        ZoneOffsetTransition transition = rules.getTransition(localDateTime);
        if (transition == null) {
            throw new DateTimeException("Could not resolve local schedule time in " + zone);
        }
        if (gap == DstGapPolicy.REJECT) {
            throw new IllegalArgumentException("Local schedule time does not exist because of a daylight-saving transition");
        }
        return localDateTime.plus(transition.getDuration()).atZone(zone).toInstant();
    }

    public static Instant nextRunAfter(ScheduledTransfer schedule, Instant scheduledFor) {
        Objects.requireNonNull(schedule, "Schedule is required");
        Objects.requireNonNull(scheduledFor, "Scheduled occurrence is required");
        ScheduledTransferFrequency frequency = Objects.requireNonNull(schedule.getFrequency(), "Frequency is required");
        String timeZone = schedule.getSourceTimeZone() == null || schedule.getSourceTimeZone().isBlank()
                ? "UTC" : schedule.getSourceTimeZone();
        ZoneId zone = ZoneId.of(timeZone);
        LocalDateTime currentLocal = LocalDateTime.ofInstant(scheduledFor, zone);
        LocalDateTime nextLocal = switch (frequency) {
            case WEEKLY -> currentLocal.plusWeeks(1);
            case BIWEEKLY -> currentLocal.plusWeeks(2);
            case MONTHLY -> monthly(currentLocal, schedule.getRecurrenceAnchorDay(), schedule.isRecurrenceAnchorEndOfMonth());
        };
        return resolve(nextLocal, timeZone, schedule.getDstOverlapPolicy(), schedule.getDstGapPolicy());
    }

    private static LocalDateTime monthly(LocalDateTime current, Integer anchorDay, boolean anchorEndOfMonth) {
        LocalDate targetMonth = current.toLocalDate().withDayOfMonth(1).plusMonths(1);
        int requestedDay = anchorEndOfMonth
                ? targetMonth.lengthOfMonth()
                : Math.min(anchorDay == null ? current.getDayOfMonth() : anchorDay, targetMonth.lengthOfMonth());
        return LocalDateTime.of(targetMonth.withDayOfMonth(requestedDay), LocalTime.from(current));
    }
}
