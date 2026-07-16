package com.suhasan.finance.transaction_service.outcome.service;

import com.suhasan.finance.transaction_service.entity.ScheduledTransfer;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferType;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.ScenarioRequest;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.ScheduledCashflowSnapshot;
import com.suhasan.finance.transaction_service.service.ScheduledTransferCadence;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
public class OutcomeScheduledTransferForecaster {

    public List<ScheduledCashflowSnapshot> forecast(ScenarioRequest request, String userId,
                                                     Set<String> selectedAccounts,
                                                     List<ScheduledTransfer> schedules) {
        ZoneId zone = ZoneId.of(request.timeZone());
        LocalDate horizonEnd = request.horizonStart().plusDays(request.horizonDays() - 1L);
        List<ScheduledCashflowSnapshot> events = new ArrayList<>();

        for (ScheduledTransfer schedule : schedules) {
            if (!userId.equals(schedule.getUserId())
                    || schedule.getStatus() != ScheduledTransferStatus.ACTIVE) {
                continue;
            }
            boolean outgoing = selectedAccounts.contains(schedule.getFromAccountId());
            boolean incoming = selectedAccounts.contains(schedule.getToAccountId());
            BigDecimal signedAmount = (outgoing ? schedule.getAmount().negate() : BigDecimal.ZERO)
                    .add(incoming ? schedule.getAmount() : BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            if (signedAmount.signum() == 0) {
                continue;
            }

            Instant occurrence = schedule.getNextRunAt();
            int occurrenceGuard = 0;
            while (occurrence != null && occurrenceGuard++ < 100) {
                if (schedule.getEndAt() != null && occurrence.isAfter(schedule.getEndAt())) {
                    break;
                }
                LocalDate localDate = occurrence.atZone(zone).toLocalDate();
                if (localDate.isAfter(horizonEnd)) {
                    break;
                }
                if (!localDate.isBefore(request.horizonStart())) {
                    String cadence = schedule.getScheduleType() == ScheduledTransferType.ONE_TIME
                            ? "ONE_TIME" : schedule.getFrequency().name();
                    events.add(new ScheduledCashflowSnapshot(
                            "schedule:" + schedule.getScheduleId() + ":" + occurrence,
                            schedule.getScheduleId(), occurrence, localDate, signedAmount,
                            schedule.getCurrency(), schedule.getStatus().name(), cadence, zone.getId(),
                            schedule.getDescription() == null || schedule.getDescription().isBlank()
                                    ? "Scheduled transfer" : schedule.getDescription(),
                            schedule.getFromAccountId(), schedule.getToAccountId()));
                }
                if (schedule.getScheduleType() == ScheduledTransferType.ONE_TIME) {
                    break;
                }
                occurrence = ScheduledTransferCadence.nextRunAfter(schedule, occurrence);
            }
        }

        return events.stream()
                .sorted(Comparator.comparing(ScheduledCashflowSnapshot::date)
                        .thenComparing(ScheduledCashflowSnapshot::scheduledFor)
                        .thenComparing(ScheduledCashflowSnapshot::eventId))
                .toList();
    }
}
