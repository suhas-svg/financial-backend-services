package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.ScheduledTransferCreateRequest;
import com.suhasan.finance.transaction_service.dto.ScheduledTransferResponse;
import com.suhasan.finance.transaction_service.dto.ScheduledTransferRunResponse;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.ScheduledTransfer;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferFrequency;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferRun;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferRunStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferStatus;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferType;
import com.suhasan.finance.transaction_service.repository.ScheduledTransferRepository;
import com.suhasan.finance.transaction_service.repository.ScheduledTransferRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTransferService {

    private final ScheduledTransferRepository scheduleRepository;
    private final ScheduledTransferRunRepository runRepository;
    private final TransactionService transactionService;
    private final ResilientAccountServiceClient accountServiceClient;
    private final MetricsService metricsService;

    @Transactional
    public ScheduledTransferResponse create(ScheduledTransferCreateRequest request, String userId) {
        validateCreateRequest(request, userId);

        ScheduledTransfer schedule = ScheduledTransfer.builder()
                .scheduleId(UUID.randomUUID().toString())
                .userId(userId)
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency() == null ? "USD" : request.getCurrency())
                .description(request.getDescription())
                .reference(request.getReference())
                .scheduleType(request.getScheduleType())
                .frequency(request.getFrequency())
                .nextRunAt(request.getFirstRunAt())
                .endAt(request.getEndAt())
                .status(ScheduledTransferStatus.ACTIVE)
                .build();

        ScheduledTransfer saved = scheduleRepository.save(schedule);
        emitNotification(saved, "SCHEDULED_TRANSFER_CREATED", "INFO", "Scheduled transfer created",
                "Your scheduled transfer has been created.", "created");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ScheduledTransferResponse> list(String userId, ScheduledTransferStatus status, Pageable pageable) {
        if (status == null) {
            return scheduleRepository.findByUserId(userId, pageable).map(this::toResponse);
        }
        return scheduleRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("userId"), userId),
                cb.equal(root.get("status"), status)), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ScheduledTransferResponse get(String scheduleId, String userId) {
        return toResponse(findOwned(scheduleId, userId));
    }

    @Transactional
    public ScheduledTransferResponse pause(String scheduleId, String userId) {
        ScheduledTransfer schedule = findOwned(scheduleId, userId);
        requireStatus(schedule, ScheduledTransferStatus.ACTIVE, "Only active scheduled transfers can be paused");
        schedule.setStatus(ScheduledTransferStatus.PAUSED);
        ScheduledTransfer saved = scheduleRepository.save(schedule);
        emitNotification(saved, "SCHEDULED_TRANSFER_PAUSED", "INFO", "Scheduled transfer paused",
                "Your scheduled transfer has been paused.", "paused");
        return toResponse(saved);
    }

    @Transactional
    public ScheduledTransferResponse resume(String scheduleId, String userId) {
        ScheduledTransfer schedule = findOwned(scheduleId, userId);
        requireStatus(schedule, ScheduledTransferStatus.PAUSED, "Only paused scheduled transfers can be resumed");
        schedule.setStatus(ScheduledTransferStatus.ACTIVE);
        ScheduledTransfer saved = scheduleRepository.save(schedule);
        emitNotification(saved, "SCHEDULED_TRANSFER_RESUMED", "INFO", "Scheduled transfer resumed",
                "Your scheduled transfer has been resumed.", "resumed");
        return toResponse(saved);
    }

    @Transactional
    public ScheduledTransferResponse cancel(String scheduleId, String userId) {
        ScheduledTransfer schedule = findOwned(scheduleId, userId);
        if (schedule.getStatus() == ScheduledTransferStatus.COMPLETED
                || schedule.getStatus() == ScheduledTransferStatus.CANCELED) {
            throw new IllegalStateException("Only active or paused scheduled transfers can be canceled");
        }
        schedule.setStatus(ScheduledTransferStatus.CANCELED);
        ScheduledTransfer saved = scheduleRepository.save(schedule);
        emitNotification(saved, "SCHEDULED_TRANSFER_CANCELED", "INFO", "Scheduled transfer canceled",
                "Your scheduled transfer has been canceled.", "canceled");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ScheduledTransferRunResponse> listRuns(String scheduleId, String userId, Pageable pageable) {
        findOwned(scheduleId, userId);
        return runRepository.findByScheduleScheduleIdOrderByScheduledForDesc(scheduleId, pageable)
                .map(this::toRunResponse);
    }

    @Transactional
    public int executeDueTransfers(Instant now, int batchSize) {
        int size = Math.max(1, batchSize);
        var dueSchedules = scheduleRepository.findDueActiveForUpdate(now, PageRequest.of(0, size));
        metricsService.recordScheduledTransferDue(dueSchedules.size());

        int processed = 0;
        for (ScheduledTransfer schedule : dueSchedules) {
            Instant scheduledFor = schedule.getNextRunAt();
            if (runRepository.existsByScheduleScheduleIdAndScheduledFor(schedule.getScheduleId(), scheduledFor)) {
                metricsService.recordScheduledTransferDuplicatePrevented();
                continue;
            }
            executeOne(schedule, scheduledFor, now);
            processed++;
        }
        return processed;
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

    private void executeOne(ScheduledTransfer schedule, Instant scheduledFor, Instant startedAt) {
        String idempotencyKey = "scheduled-transfer:%s:%s".formatted(schedule.getScheduleId(), scheduledFor);
        ScheduledTransferRun run = ScheduledTransferRun.builder()
                .runId(UUID.randomUUID().toString())
                .schedule(schedule)
                .scheduledFor(scheduledFor)
                .startedAt(startedAt)
                .status(ScheduledTransferRunStatus.PROCESSING)
                .idempotencyKey(idempotencyKey)
                .build();
        runRepository.save(run);
        metricsService.recordScheduledTransferClaimed();

        long started = System.currentTimeMillis();
        try {
            TransactionResponse transaction = transactionService.processTransfer(toTransferRequest(schedule),
                    schedule.getUserId(), idempotencyKey);
            run.setStatus(ScheduledTransferRunStatus.COMPLETED);
            run.setTransactionId(transaction == null ? null : transaction.getTransactionId());
            run.setCompletedAt(Instant.now());
            schedule.setLastRunAt(scheduledFor);
            advanceOrComplete(schedule, scheduledFor);
            runRepository.save(run);
            scheduleRepository.save(schedule);
            metricsService.recordScheduledTransferCompleted(System.currentTimeMillis() - started);
            emitNotification(schedule, "SCHEDULED_TRANSFER_EXECUTED", "SUCCESS", "Scheduled transfer executed",
                    "Your scheduled transfer was executed successfully.", "executed:%s".formatted(scheduledFor));
        } catch (RuntimeException e) {
            run.setStatus(ScheduledTransferRunStatus.FAILED);
            run.setFailureReason(sanitizeFailureReason(e));
            run.setCompletedAt(Instant.now());
            schedule.setLastRunAt(scheduledFor);
            if (schedule.getScheduleType() == ScheduledTransferType.ONE_TIME) {
                schedule.setStatus(ScheduledTransferStatus.COMPLETED);
            } else {
                advanceOrComplete(schedule, scheduledFor);
            }
            runRepository.save(run);
            scheduleRepository.save(schedule);
            metricsService.recordScheduledTransferFailed(System.currentTimeMillis() - started);
            emitNotification(schedule, "SCHEDULED_TRANSFER_FAILED", "WARNING", "Scheduled transfer failed",
                    "Your scheduled transfer failed: %s".formatted(run.getFailureReason()),
                    "failed:%s".formatted(scheduledFor));
        }
    }

    private void validateCreateRequest(ScheduledTransferCreateRequest request, String userId) {
        if (request == null) {
            throw new IllegalArgumentException("Scheduled transfer request is required");
        }
        if (Objects.equals(request.getFromAccountId(), request.getToAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }
        if (request.getFirstRunAt() == null || !request.getFirstRunAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("First run time must be in the future");
        }
        if (request.getScheduleType() == ScheduledTransferType.ONE_TIME && request.getFrequency() != null) {
            throw new IllegalArgumentException("One-time scheduled transfers must not include a frequency");
        }
        if (request.getScheduleType() == ScheduledTransferType.RECURRING && request.getFrequency() == null) {
            throw new IllegalArgumentException("Recurring scheduled transfers require a frequency");
        }
        if (request.getEndAt() != null && !request.getEndAt().isAfter(request.getFirstRunAt())) {
            throw new IllegalArgumentException("End time must be after first run time");
        }

        AccountDto source = accountServiceClient.getAccount(request.getFromAccountId());
        if (source == null || Boolean.FALSE.equals(source.getActive()) || !source.allowsDebits()) {
            throw new IllegalArgumentException("Invalid source account");
        }
        if (!Objects.equals(source.getOwnerId(), userId)) {
            throw new AccessDeniedException("User does not own source account");
        }
    }

    private ScheduledTransfer findOwned(String scheduleId, String userId) {
        return scheduleRepository.findByScheduleIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new AccessDeniedException("Scheduled transfer not found"));
    }

    private void requireStatus(ScheduledTransfer schedule, ScheduledTransferStatus status, String message) {
        if (schedule.getStatus() != status) {
            throw new IllegalStateException(message);
        }
    }

    private TransferRequest toTransferRequest(ScheduledTransfer schedule) {
        return TransferRequest.builder()
                .fromAccountId(schedule.getFromAccountId())
                .toAccountId(schedule.getToAccountId())
                .amount(schedule.getAmount())
                .currency(schedule.getCurrency())
                .description(schedule.getDescription())
                .reference(schedule.getReference())
                .build();
    }

    private void advanceOrComplete(ScheduledTransfer schedule, Instant scheduledFor) {
        if (schedule.getScheduleType() == ScheduledTransferType.ONE_TIME) {
            schedule.setStatus(ScheduledTransferStatus.COMPLETED);
            return;
        }
        Instant next = nextRunAfter(scheduledFor, schedule.getFrequency());
        if (schedule.getEndAt() != null && next.isAfter(schedule.getEndAt())) {
            schedule.setStatus(ScheduledTransferStatus.COMPLETED);
            schedule.setNextRunAt(next);
            return;
        }
        schedule.setNextRunAt(next);
    }

    private String sanitizeFailureReason(RuntimeException e) {
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        reason = reason.replace('\r', ' ').replace('\n', ' ').trim();
        return reason.length() <= 1000 ? reason : reason.substring(0, 1000);
    }

    private ScheduledTransferResponse toResponse(ScheduledTransfer schedule) {
        Page<ScheduledTransferRun> latestRunPage = runRepository
                .findByScheduleScheduleIdOrderByScheduledForDesc(schedule.getScheduleId(), PageRequest.of(0, 1));
        ScheduledTransferRun lastRun = latestRunPage == null
                ? null
                : latestRunPage.stream().findFirst().orElse(null);
        return ScheduledTransferResponse.builder()
                .scheduleId(schedule.getScheduleId())
                .userId(schedule.getUserId())
                .fromAccountId(schedule.getFromAccountId())
                .toAccountId(schedule.getToAccountId())
                .amount(schedule.getAmount())
                .currency(schedule.getCurrency())
                .description(schedule.getDescription())
                .reference(schedule.getReference())
                .scheduleType(schedule.getScheduleType())
                .frequency(schedule.getFrequency())
                .nextRunAt(schedule.getNextRunAt())
                .endAt(schedule.getEndAt())
                .status(schedule.getStatus())
                .lastRunAt(schedule.getLastRunAt())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .version(schedule.getVersion())
                .lastRunStatus(lastRun == null ? null : lastRun.getStatus())
                .lastRunFailureReason(lastRun == null ? null : lastRun.getFailureReason())
                .lastTransactionId(lastRun == null ? null : lastRun.getTransactionId())
                .build();
    }

    private ScheduledTransferRunResponse toRunResponse(ScheduledTransferRun run) {
        return ScheduledTransferRunResponse.builder()
                .runId(run.getRunId())
                .scheduleId(run.getSchedule().getScheduleId())
                .scheduledFor(run.getScheduledFor())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .status(run.getStatus())
                .transactionId(run.getTransactionId())
                .idempotencyKey(run.getIdempotencyKey())
                .failureReason(run.getFailureReason())
                .build();
    }

    private void emitNotification(ScheduledTransfer schedule, String type, String severity, String title,
                                  String message, String lifecycleKey) {
        try {
            accountServiceClient.createNotification(ResilientAccountServiceClient.NotificationRequest.builder()
                    .userId(schedule.getUserId())
                    .type(type)
                    .severity(severity)
                    .title(title)
                    .message(message)
                    .sourceType("SCHEDULED_TRANSFER")
                    .sourceId(schedule.getScheduleId())
                    .dedupeKey("scheduled-transfer:%s:%s".formatted(schedule.getScheduleId(), lifecycleKey))
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to create scheduled transfer notification for schedule {}: {}",
                    schedule.getScheduleId(), e.getMessage());
        }
    }
}
