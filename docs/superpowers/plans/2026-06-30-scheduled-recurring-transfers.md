# Scheduled And Recurring Transfers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build customer-owned one-time and recurring scheduled transfers that execute through the existing transaction-service transfer workflow.

**Architecture:** Keep ownership in `transaction-service`: schedules and execution attempts are persisted there, a scheduled worker claims due schedules, and each execution calls the existing `TransactionService.processTransfer` method. `account-service` changes are limited to accepting new notification enum values and source type. The React frontend adds a customer `/scheduled-transfers` page using existing query/form/component patterns.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, Micrometer, JUnit/Mockito/MockMvc, React 18, Vite, TypeScript, React Query, React Hook Form, Zod, Testing Library.

---

## File Structure

Create or modify these files:

- Create `transaction-service/src/main/resources/db/migration/V19__create_scheduled_transfers.sql`: schedules and execution attempts schema.
- Create `transaction-service/src/main/java/com/suhasan/finance/transaction_service/entity/ScheduledTransfer.java`: schedule entity.
- Create `transaction-service/src/main/java/com/suhasan/finance/transaction_service/entity/ScheduledTransferRun.java`: execution attempt entity.
- Create `transaction-service/src/main/java/com/suhasan/finance/transaction_service/entity/ScheduledTransferStatus.java`: `ACTIVE`, `PAUSED`, `CANCELED`, `COMPLETED`.
- Create `transaction-service/src/main/java/com/suhasan/finance/transaction_service/entity/ScheduledTransferType.java`: `ONE_TIME`, `RECURRING`.
- Create `transaction-service/src/main/java/com/suhasan/finance/transaction_service/entity/ScheduledTransferFrequency.java`: `WEEKLY`, `BIWEEKLY`, `MONTHLY`.
- Create `transaction-service/src/main/java/com/suhasan/finance/transaction_service/entity/ScheduledTransferRunStatus.java`: `PROCESSING`, `COMPLETED`, `FAILED`, `SKIPPED`.
- Create `transaction-service/src/main/java/com/suhasan/finance/transaction_service/repository/ScheduledTransferRepository.java`.
- Create `transaction-service/src/main/java/com/suhasan/finance/transaction_service/repository/ScheduledTransferRunRepository.java`.
- Create DTOs under `transaction-service/src/main/java/com/suhasan/finance/transaction_service/dto/`: `ScheduledTransferCreateRequest`, `ScheduledTransferResponse`, `ScheduledTransferRunResponse`.
- Create `transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/ScheduledTransferService.java`: creation, list, detail, state transitions, execution logic.
- Create `transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/ScheduledTransferScheduler.java`: scheduled tick wrapper.
- Create `transaction-service/src/main/java/com/suhasan/finance/transaction_service/controller/ScheduledTransferController.java`.
- Modify `transaction-service/src/main/java/com/suhasan/finance/transaction_service/security/SecurityConfig.java`: authenticate `/api/scheduled-transfers/**`.
- Modify `transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/MetricsService.java`: scheduled-transfer counters/timer.
- Modify `account-service/src/main/java/com/suhasan/finance/account_service/entity/NotificationType.java`.
- Modify `account-service/src/main/java/com/suhasan/finance/account_service/entity/NotificationSourceType.java`.
- Modify `frontend/src/types.ts`: scheduled transfer types and notification enums.
- Modify `frontend/src/lib/schemas.ts`: scheduled transfer form schema.
- Modify `frontend/src/lib/queries.ts`: scheduled transfer API functions.
- Modify `frontend/src/components/CustomerLayout.tsx`: add navigation item.
- Modify `frontend/src/App.tsx`: add route.
- Create `frontend/src/pages/ScheduledTransfersPage.tsx`.
- Extend `frontend/src/lib/api.test.ts`, `frontend/src/lib/schemas.test.ts`, and `frontend/src/test/app.component.test.tsx`.

## Task 1: Account-Service Notification Enum Support

**Files:**
- Modify: `account-service/src/main/java/com/suhasan/finance/account_service/entity/NotificationType.java`
- Modify: `account-service/src/main/java/com/suhasan/finance/account_service/entity/NotificationSourceType.java`
- Test: `account-service/src/test/java/com/suhasan/finance/account_service/service/NotificationServiceTest.java`

- [ ] **Step 1: Write the failing notification enum test**

Add this test to `NotificationServiceTest`:

```java
@Test
void createNotification_acceptsScheduledTransferLifecycleType() {
    NotificationCreateRequest request = NotificationCreateRequest.builder()
            .userId("customer")
            .type(NotificationType.SCHEDULED_TRANSFER_CREATED)
            .severity(NotificationSeverity.INFO)
            .title("Scheduled transfer created")
            .message("Your scheduled transfer was created.")
            .sourceType(NotificationSourceType.SCHEDULED_TRANSFER)
            .sourceId("schedule-1")
            .dedupeKey("scheduled-transfer:schedule-1:created")
            .build();

    NotificationResponse response = notificationService.createNotification(request);

    assertThat(response.getType()).isEqualTo(NotificationType.SCHEDULED_TRANSFER_CREATED);
    assertThat(response.getSourceType()).isEqualTo(NotificationSourceType.SCHEDULED_TRANSFER);
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
cd account-service
.\mvnw.cmd -q "-Dtest=NotificationServiceTest#createNotification_acceptsScheduledTransferLifecycleType" test
```

Expected: compilation fails because the new enum constants do not exist.

- [ ] **Step 3: Add scheduled-transfer notification enum constants**

Update `NotificationType.java`:

```java
package com.suhasan.finance.account_service.entity;

public enum NotificationType {
    TRANSACTION_COMPLETED,
    TRANSACTION_FAILED,
    ACCOUNT_FROZEN,
    ACCOUNT_UNFROZEN,
    DISPUTE_CREATED,
    DISPUTE_STATUS_UPDATED,
    SCHEDULED_TRANSFER_CREATED,
    SCHEDULED_TRANSFER_EXECUTED,
    SCHEDULED_TRANSFER_FAILED,
    SCHEDULED_TRANSFER_PAUSED,
    SCHEDULED_TRANSFER_RESUMED,
    SCHEDULED_TRANSFER_CANCELED
}
```

Update `NotificationSourceType.java`:

```java
package com.suhasan.finance.account_service.entity;

public enum NotificationSourceType {
    ACCOUNT,
    TRANSACTION,
    DISPUTE,
    SCHEDULED_TRANSFER
}
```

- [ ] **Step 4: Run notification and account-service tests**

Run:

```powershell
cd account-service
.\mvnw.cmd -q "-Dtest=NotificationServiceTest" test
.\mvnw.cmd -q test
```

Expected: both commands pass.

- [ ] **Step 5: Commit Task 1**

```powershell
git add account-service/src/main/java/com/suhasan/finance/account_service/entity/NotificationType.java account-service/src/main/java/com/suhasan/finance/account_service/entity/NotificationSourceType.java account-service/src/test/java/com/suhasan/finance/account_service/service/NotificationServiceTest.java
git commit -m "feat(notifications): support scheduled transfer events"
```

## Task 2: Transaction-Service Persistence And Recurrence Domain

**Files:**
- Create: `transaction-service/src/main/resources/db/migration/V19__create_scheduled_transfers.sql`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/entity/ScheduledTransfer*.java`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/repository/ScheduledTransferRepository.java`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/repository/ScheduledTransferRunRepository.java`
- Create: `transaction-service/src/test/java/com/suhasan/finance/transaction_service/service/ScheduledTransferRecurrenceTest.java`
- Create: `transaction-service/src/test/java/com/suhasan/finance/transaction_service/repository/ScheduledTransferMigrationTest.java`

- [ ] **Step 1: Write recurrence tests**

Create `ScheduledTransferRecurrenceTest.java`:

```java
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
```

- [ ] **Step 2: Run recurrence tests to verify they fail**

Run:

```powershell
cd transaction-service
.\mvnw.cmd -q "-Dtest=ScheduledTransferRecurrenceTest" test
```

Expected: compilation fails because scheduled-transfer domain classes do not exist.

- [ ] **Step 3: Add enums and entities**

Create enums:

```java
public enum ScheduledTransferStatus { ACTIVE, PAUSED, CANCELED, COMPLETED }
public enum ScheduledTransferType { ONE_TIME, RECURRING }
public enum ScheduledTransferFrequency { WEEKLY, BIWEEKLY, MONTHLY }
public enum ScheduledTransferRunStatus { PROCESSING, COMPLETED, FAILED, SKIPPED }
```

Create `ScheduledTransfer.java` with fields matching the approved spec. Use `String scheduleId`, `String userId`, account IDs, `BigDecimal amount`, `String currency`, description, reference, enum fields, `Instant nextRunAt`, `Instant endAt`, `Instant lastRunAt`, `Instant createdAt`, `Instant updatedAt`, and `Long version`. Use `@PrePersist` and `@PreUpdate` to set IDs and timestamps.

Create `ScheduledTransferRun.java` with `String runId`, `ScheduledTransfer schedule`, `Instant scheduledFor`, `Instant startedAt`, `Instant completedAt`, `ScheduledTransferRunStatus status`, `String transactionId`, `String idempotencyKey`, and `String failureReason`.

- [ ] **Step 4: Add Flyway migration**

Create `V19__create_scheduled_transfers.sql`:

```sql
CREATE TABLE scheduled_transfers (
    schedule_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    from_account_id VARCHAR(64) NOT NULL,
    to_account_id VARCHAR(64) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(500),
    reference VARCHAR(100),
    schedule_type VARCHAR(32) NOT NULL,
    frequency VARCHAR(32),
    next_run_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    last_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_scheduled_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_scheduled_transfer_frequency CHECK (
        (schedule_type = 'ONE_TIME' AND frequency IS NULL)
        OR (schedule_type = 'RECURRING' AND frequency IS NOT NULL)
    )
);

CREATE TABLE scheduled_transfer_runs (
    run_id VARCHAR(36) PRIMARY KEY,
    schedule_id VARCHAR(36) NOT NULL,
    scheduled_for TIMESTAMP NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    transaction_id VARCHAR(36),
    idempotency_key VARCHAR(160) NOT NULL,
    failure_reason VARCHAR(1000),
    CONSTRAINT fk_scheduled_transfer_runs_schedule
        FOREIGN KEY (schedule_id) REFERENCES scheduled_transfers(schedule_id),
    CONSTRAINT uq_scheduled_transfer_run UNIQUE (schedule_id, scheduled_for)
);

CREATE INDEX idx_scheduled_transfers_user_id ON scheduled_transfers(user_id);
CREATE INDEX idx_scheduled_transfers_status_next_run ON scheduled_transfers(status, next_run_at);
CREATE INDEX idx_scheduled_transfer_runs_schedule_id ON scheduled_transfer_runs(schedule_id);
CREATE INDEX idx_scheduled_transfer_runs_status ON scheduled_transfer_runs(status);
CREATE INDEX idx_scheduled_transfer_runs_transaction_id ON scheduled_transfer_runs(transaction_id);
```

- [ ] **Step 5: Add repositories**

`ScheduledTransferRepository` should extend `JpaRepository<ScheduledTransfer, String>` and `JpaSpecificationExecutor<ScheduledTransfer>`. Add:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from ScheduledTransfer s where s.status = 'ACTIVE' and s.nextRunAt <= :now order by s.nextRunAt asc")
List<ScheduledTransfer> findDueActiveForUpdate(@Param("now") Instant now, Pageable pageable);

Page<ScheduledTransfer> findByUserId(String userId, Pageable pageable);
Optional<ScheduledTransfer> findByScheduleIdAndUserId(String scheduleId, String userId);
```

`ScheduledTransferRunRepository` should extend `JpaRepository<ScheduledTransferRun, String>`. Add:

```java
boolean existsByScheduleScheduleIdAndScheduledFor(String scheduleId, Instant scheduledFor);
Page<ScheduledTransferRun> findByScheduleScheduleIdOrderByScheduledForDesc(String scheduleId, Pageable pageable);
```

- [ ] **Step 6: Run persistence tests and compile**

Run:

```powershell
cd transaction-service
.\mvnw.cmd -q "-Dtest=ScheduledTransferRecurrenceTest" test
.\mvnw.cmd -q -DskipTests compile
```

Expected: recurrence test passes and service compiles.

- [ ] **Step 7: Commit Task 2**

```powershell
git add transaction-service/src/main/resources/db/migration/V19__create_scheduled_transfers.sql transaction-service/src/main/java/com/suhasan/finance/transaction_service/entity/ScheduledTransfer*.java transaction-service/src/main/java/com/suhasan/finance/transaction_service/repository/ScheduledTransferRepository.java transaction-service/src/main/java/com/suhasan/finance/transaction_service/repository/ScheduledTransferRunRepository.java transaction-service/src/test/java/com/suhasan/finance/transaction_service/service/ScheduledTransferRecurrenceTest.java
git commit -m "feat(transactions): add scheduled transfer persistence"
```

## Task 3: Scheduled Transfer Service And Worker

**Files:**
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/dto/ScheduledTransferCreateRequest.java`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/dto/ScheduledTransferResponse.java`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/dto/ScheduledTransferRunResponse.java`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/ScheduledTransferService.java`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/ScheduledTransferScheduler.java`
- Modify: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/MetricsService.java`
- Test: `transaction-service/src/test/java/com/suhasan/finance/transaction_service/service/ScheduledTransferServiceTest.java`

- [ ] **Step 1: Write service tests for create and state transitions**

Create `ScheduledTransferServiceTest.java` with tests shaped like this:

```java
@Test
void createScheduleRejectsPastFirstRunAt() {
    ScheduledTransferCreateRequest request = baseCreateRequest();
    request.setFirstRunAt(Instant.now().minusSeconds(60));

    assertThatThrownBy(() -> service.create(request, "customer"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("future");
}

@Test
void createScheduleRejectsDifferentOwnerSourceAccount() {
    ScheduledTransferCreateRequest request = baseCreateRequest();
    AccountDto source = AccountDto.builder()
            .id(101L)
            .ownerId("other-user")
            .accountType("CHECKING")
            .active(true)
            .build();
    when(accountServiceClient.getAccount("101")).thenReturn(source);

    assertThatThrownBy(() -> service.create(request, "customer"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("source account");
}

@Test
void pauseResumeCancelRequireOwnerAndValidState() {
    ScheduledTransfer schedule = activeSchedule("schedule-1", "customer");
    when(scheduleRepository.findByScheduleIdAndUserId("schedule-1", "customer"))
            .thenReturn(Optional.of(schedule));
    when(scheduleRepository.save(any(ScheduledTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ScheduledTransferResponse paused = service.pause("schedule-1", "customer");
    assertThat(paused.getStatus()).isEqualTo(ScheduledTransferStatus.PAUSED);

    ScheduledTransferResponse resumed = service.resume("schedule-1", "customer");
    assertThat(resumed.getStatus()).isEqualTo(ScheduledTransferStatus.ACTIVE);

    ScheduledTransferResponse canceled = service.cancel("schedule-1", "customer");
    assertThat(canceled.getStatus()).isEqualTo(ScheduledTransferStatus.CANCELED);
}
```

Use mocked `ScheduledTransferRepository`, `ScheduledTransferRunRepository`, `TransactionService`, `ResilientAccountServiceClient`, and `MetricsService`. Use `AccountDto.builder().id(101L).ownerId("customer").accountType("CHECKING").active(true).build()` for source account ownership checks.

- [ ] **Step 2: Write worker execution tests**

Add tests:

```java
@Test
void executeDueScheduleCreatesTransferRunAndLinksTransaction() {
    ScheduledTransfer schedule = activeSchedule("schedule-1", "customer");
    schedule.setNextRunAt(Instant.parse("2026-07-01T09:00:00Z"));
    when(scheduleRepository.findDueActiveForUpdate(eq(Instant.parse("2026-07-01T09:01:00Z")), any()))
            .thenReturn(List.of(schedule));
    when(runRepository.existsByScheduleScheduleIdAndScheduledFor("schedule-1", schedule.getNextRunAt()))
            .thenReturn(false);
    when(runRepository.save(any(ScheduledTransferRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(transactionService.processTransfer(any(TransferRequest.class), eq("customer"), anyString()))
            .thenReturn(TransactionResponse.builder().transactionId("txn-1").build());

    int processed = service.executeDueTransfers(Instant.parse("2026-07-01T09:01:00Z"), 50);

    assertThat(processed).isEqualTo(1);
    verify(transactionService).processTransfer(argThat(request ->
            "101".equals(request.getFromAccountId())
                    && "102".equals(request.getToAccountId())
                    && request.getAmount().compareTo(new BigDecimal("125.00")) == 0), eq("customer"), contains("schedule-1"));
    verify(runRepository, atLeastOnce()).save(argThat(run ->
            run.getStatus() == ScheduledTransferRunStatus.COMPLETED
                    && "txn-1".equals(run.getTransactionId())));
}

@Test
void failedOneTimeExecutionCompletesScheduleWithoutRetry() {
    ScheduledTransfer schedule = activeSchedule("schedule-1", "customer");
    schedule.setScheduleType(ScheduledTransferType.ONE_TIME);
    schedule.setFrequency(null);
    schedule.setNextRunAt(Instant.parse("2026-07-01T09:00:00Z"));
    when(scheduleRepository.findDueActiveForUpdate(any(), any())).thenReturn(List.of(schedule));
    when(runRepository.existsByScheduleScheduleIdAndScheduledFor("schedule-1", schedule.getNextRunAt()))
            .thenReturn(false);
    when(runRepository.save(any(ScheduledTransferRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(transactionService.processTransfer(any(TransferRequest.class), eq("customer"), anyString()))
            .thenThrow(new RuntimeException("Insufficient funds"));

    int processed = service.executeDueTransfers(Instant.parse("2026-07-01T09:01:00Z"), 50);

    assertThat(processed).isEqualTo(1);
    assertThat(schedule.getStatus()).isEqualTo(ScheduledTransferStatus.COMPLETED);
    verify(runRepository, atLeastOnce()).save(argThat(run ->
            run.getStatus() == ScheduledTransferRunStatus.FAILED
                    && run.getFailureReason().contains("Insufficient funds")));
}

@Test
void duplicateRunIsSkippedWhenRunAlreadyExists() {
    ScheduledTransfer schedule = activeSchedule("schedule-1", "customer");
    schedule.setNextRunAt(Instant.parse("2026-07-01T09:00:00Z"));
    when(scheduleRepository.findDueActiveForUpdate(any(), any())).thenReturn(List.of(schedule));
    when(runRepository.existsByScheduleScheduleIdAndScheduledFor("schedule-1", schedule.getNextRunAt()))
            .thenReturn(true);

    int processed = service.executeDueTransfers(Instant.parse("2026-07-01T09:01:00Z"), 50);

    assertThat(processed).isZero();
    verify(transactionService, never()).processTransfer(any(), anyString(), anyString());
    verify(metricsService).recordScheduledTransferDuplicatePrevented();
}
```

Expected behavior:

- Success calls `transactionService.processTransfer(TransferRequest, String, String)`.
- Success marks run `COMPLETED`, stores transaction ID, and advances or completes schedule.
- Failure stores sanitized failure reason.
- Duplicate run path does not call `processTransfer(TransferRequest, String, String)`.

- [ ] **Step 3: Run tests to verify they fail**

Run:

```powershell
cd transaction-service
.\mvnw.cmd -q "-Dtest=ScheduledTransferServiceTest" test
```

Expected: compilation fails because DTOs/service do not exist.

- [ ] **Step 4: Implement DTOs**

`ScheduledTransferCreateRequest`:

```java
@Data
public class ScheduledTransferCreateRequest {
    @NotBlank private String fromAccountId;
    @NotBlank private String toAccountId;
    @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") private BigDecimal amount;
    @Pattern(regexp = "USD|EUR|GBP") private String currency = "USD";
    @Size(max = 500) private String description;
    @Size(max = 100) private String reference;
    @NotNull private ScheduledTransferType scheduleType;
    private ScheduledTransferFrequency frequency;
    @NotNull private Instant firstRunAt;
    private Instant endAt;
}
```

`ScheduledTransferResponse` includes every schedule field plus `lastRunStatus`, `lastRunFailureReason`, and `lastTransactionId` for the list page.

`ScheduledTransferRunResponse` includes every run field.

- [ ] **Step 5: Implement `ScheduledTransferService`**

Required public methods:

```java
@Transactional
public ScheduledTransferResponse create(ScheduledTransferCreateRequest request, String userId)

@Transactional(readOnly = true)
public Page<ScheduledTransferResponse> list(String userId, ScheduledTransferStatus status, Pageable pageable)

@Transactional(readOnly = true)
public ScheduledTransferResponse get(String scheduleId, String userId)

@Transactional
public ScheduledTransferResponse pause(String scheduleId, String userId)

@Transactional
public ScheduledTransferResponse resume(String scheduleId, String userId)

@Transactional
public ScheduledTransferResponse cancel(String scheduleId, String userId)

@Transactional(readOnly = true)
public Page<ScheduledTransferRunResponse> listRuns(String scheduleId, String userId, Pageable pageable)

@Transactional
public int executeDueTransfers(Instant now, int batchSize)

public static Instant nextRunAfter(Instant previous, ScheduledTransferFrequency frequency)
```

Key implementation details:

- Validate source account ownership with `accountServiceClient.getAccount(fromAccountId)`.
- Reject same source and destination.
- Reject `firstRunAt` that is not after `Instant.now()`.
- Enforce `frequency` nullability based on schedule type.
- Use `TransferRequest.builder()` when executing.
- Use idempotency key `scheduled-transfer:%s:%s`.formatted(scheduleId, scheduledFor).
- Sanitize failure reason to 1000 characters and remove line breaks.
- Emit notifications by calling `accountServiceClient.createNotification(NotificationRequest)` inside `try/catch`.

- [ ] **Step 6: Implement scheduler wrapper**

Create `ScheduledTransferScheduler`:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTransferScheduler {
    private final ScheduledTransferService scheduledTransferService;

    @Scheduled(fixedDelayString = "${scheduled-transfers.worker.fixed-delay-ms:60000}")
    public void runDueTransfers() {
        int processed = scheduledTransferService.executeDueTransfers(Instant.now(), 50);
        if (processed > 0) {
            log.info("Processed {} due scheduled transfers", processed);
        }
    }
}
```

- [ ] **Step 7: Add metrics methods**

Add methods to `MetricsService`:

```java
public void recordScheduledTransferDue(int count) {
    scheduledTransferDueCounter.increment(Math.max(0, count));
}

public void recordScheduledTransferClaimed() {
    scheduledTransferClaimedCounter.increment();
}

public void recordScheduledTransferCompleted(long durationMs) {
    scheduledTransferCompletedCounter.increment();
    scheduledTransferExecutionTimer.record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
}

public void recordScheduledTransferFailed(long durationMs) {
    scheduledTransferFailedCounter.increment();
    scheduledTransferExecutionTimer.record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
}

public void recordScheduledTransferDuplicatePrevented() {
    scheduledTransferDuplicatePreventedCounter.increment();
}
```

Use Micrometer `Counter` and `Timer` fields initialized in the constructor.

- [ ] **Step 8: Run service tests and compile**

Run:

```powershell
cd transaction-service
.\mvnw.cmd -q "-Dtest=ScheduledTransferServiceTest,ScheduledTransferRecurrenceTest" test
.\mvnw.cmd -q -DskipTests compile
```

Expected: tests and compile pass.

- [ ] **Step 9: Commit Task 3**

```powershell
git add transaction-service/src/main/java/com/suhasan/finance/transaction_service/dto/ScheduledTransfer*.java transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/ScheduledTransferService.java transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/ScheduledTransferScheduler.java transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/MetricsService.java transaction-service/src/test/java/com/suhasan/finance/transaction_service/service/ScheduledTransferServiceTest.java
git commit -m "feat(transactions): execute scheduled transfers"
```

## Task 4: Scheduled Transfer REST API And Security

**Files:**
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/controller/ScheduledTransferController.java`
- Modify: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/security/SecurityConfig.java`
- Test: `transaction-service/src/test/java/com/suhasan/finance/transaction_service/controller/ScheduledTransferControllerTest.java`

- [ ] **Step 1: Write controller tests**

Create tests covering:

```java
@Test
@WithMockUser(username = "customer", roles = "USER")
void createScheduleUsesCurrentCustomer()

@Test
@WithMockUser(username = "customer", roles = "USER")
void listAndGetSchedulesUseCurrentCustomer()

@Test
@WithMockUser(username = "customer", roles = "USER")
void pauseResumeCancelUseCurrentCustomer()

@Test
@WithMockUser(username = "customer", roles = "USER")
void listRunsUsesCurrentCustomer()

@Test
void unauthenticatedRequestIsUnauthorized()
```

Assert response JSON contains `scheduleId`, `status`, `scheduleType`, `frequency`, and `nextRunAt`.

- [ ] **Step 2: Run controller tests to verify they fail**

Run:

```powershell
cd transaction-service
.\mvnw.cmd -q "-Dtest=ScheduledTransferControllerTest" test
```

Expected: compilation fails because controller does not exist.

- [ ] **Step 3: Implement controller**

Create:

```java
@RestController
@RequestMapping("/api/scheduled-transfers")
@RequiredArgsConstructor
public class ScheduledTransferController {
    private final ScheduledTransferService scheduledTransferService;

    @PostMapping
    public ResponseEntity<ScheduledTransferResponse> create(
            @Valid @RequestBody ScheduledTransferCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduledTransferService.create(request, currentUser(authentication)));
    }

    @GetMapping
    public ResponseEntity<Page<ScheduledTransferResponse>> list(
            @RequestParam(required = false) ScheduledTransferStatus status,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "nextRunAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(scheduledTransferService.list(currentUser(authentication), status, pageable));
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<ScheduledTransferResponse> get(@PathVariable String scheduleId, Authentication authentication) {
        return ResponseEntity.ok(scheduledTransferService.get(scheduleId, currentUser(authentication)));
    }

    @PatchMapping("/{scheduleId}/pause")
    public ResponseEntity<ScheduledTransferResponse> pause(@PathVariable String scheduleId, Authentication authentication) {
        return ResponseEntity.ok(scheduledTransferService.pause(scheduleId, currentUser(authentication)));
    }

    @PatchMapping("/{scheduleId}/resume")
    public ResponseEntity<ScheduledTransferResponse> resume(@PathVariable String scheduleId, Authentication authentication) {
        return ResponseEntity.ok(scheduledTransferService.resume(scheduleId, currentUser(authentication)));
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<ScheduledTransferResponse> cancel(@PathVariable String scheduleId, Authentication authentication) {
        return ResponseEntity.ok(scheduledTransferService.cancel(scheduleId, currentUser(authentication)));
    }

    @GetMapping("/{scheduleId}/runs")
    public ResponseEntity<Page<ScheduledTransferRunResponse>> runs(
            @PathVariable String scheduleId,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "scheduledFor", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(scheduledTransferService.listRuns(scheduleId, currentUser(authentication), pageable));
    }

    private String currentUser(Authentication authentication) {
        return authentication != null ? authentication.getName() : "SYSTEM";
    }
}
```

- [ ] **Step 4: Update security**

Add before the transaction endpoints in `SecurityConfig`:

```java
.requestMatchers("/api/scheduled-transfers/**").authenticated()
```

- [ ] **Step 5: Run controller and focused backend tests**

Run:

```powershell
cd transaction-service
.\mvnw.cmd -q "-Dtest=ScheduledTransferControllerTest,ScheduledTransferServiceTest" test
.\mvnw.cmd -q -DskipTests compile
```

Expected: tests and compile pass.

- [ ] **Step 6: Commit Task 4**

```powershell
git add transaction-service/src/main/java/com/suhasan/finance/transaction_service/controller/ScheduledTransferController.java transaction-service/src/main/java/com/suhasan/finance/transaction_service/security/SecurityConfig.java transaction-service/src/test/java/com/suhasan/finance/transaction_service/controller/ScheduledTransferControllerTest.java
git commit -m "feat(transactions): expose scheduled transfer api"
```

## Task 5: Frontend Scheduled Transfers Page

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/lib/schemas.ts`
- Modify: `frontend/src/lib/queries.ts`
- Modify: `frontend/src/components/CustomerLayout.tsx`
- Modify: `frontend/src/App.tsx`
- Create: `frontend/src/pages/ScheduledTransfersPage.tsx`
- Test: `frontend/src/lib/api.test.ts`
- Test: `frontend/src/lib/schemas.test.ts`
- Test: `frontend/src/test/app.component.test.tsx`

- [ ] **Step 1: Add failing API and schema tests**

In `api.test.ts`, add tests asserting:

```typescript
await createScheduledTransfer(values);
expect(fetch).toHaveBeenCalledWith("/transaction-api/api/scheduled-transfers", expect.objectContaining({ method: "POST" }));

await listScheduledTransfers({ status: "ACTIVE" });
expect(fetch).toHaveBeenCalledWith(expect.stringContaining("/transaction-api/api/scheduled-transfers?"), expect.anything());

await pauseScheduledTransfer("schedule-1");
await resumeScheduledTransfer("schedule-1");
await cancelScheduledTransfer("schedule-1");
await listScheduledTransferRuns("schedule-1");
```

In `schemas.test.ts`, add tests that:

- recurring schedule requires `frequency`,
- one-time schedule rejects `frequency`,
- source and destination must differ,
- `firstRunAt` is required.

- [ ] **Step 2: Add failing component test**

In `app.component.test.tsx`, add a test that renders `/scheduled-transfers`, mocks `/api/scheduled-transfers`, submits a monthly schedule, then pauses/resumes/cancels an existing schedule. Assert the nav label `Scheduled` is visible and the API calls use transaction-service proxy routes.

- [ ] **Step 3: Run frontend tests to verify failure**

Run:

```powershell
cd frontend
npm test
```

Expected: TypeScript/test failures because types, queries, schema, and page do not exist.

- [ ] **Step 4: Add frontend types**

Add to `types.ts`:

```typescript
export type ScheduledTransferStatus = "ACTIVE" | "PAUSED" | "CANCELED" | "COMPLETED";
export type ScheduledTransferType = "ONE_TIME" | "RECURRING";
export type ScheduledTransferFrequency = "WEEKLY" | "BIWEEKLY" | "MONTHLY";
export type ScheduledTransferRunStatus = "PROCESSING" | "COMPLETED" | "FAILED" | "SKIPPED";

export type ScheduledTransfer = {
  scheduleId: string;
  userId: string;
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  currency: string;
  description?: string;
  reference?: string;
  scheduleType: ScheduledTransferType;
  frequency?: ScheduledTransferFrequency;
  nextRunAt: string;
  endAt?: string;
  status: ScheduledTransferStatus;
  lastRunAt?: string;
  lastRunStatus?: ScheduledTransferRunStatus;
  lastRunFailureReason?: string;
  lastTransactionId?: string;
};

export type ScheduledTransferRun = {
  runId: string;
  scheduleId: string;
  scheduledFor: string;
  startedAt: string;
  completedAt?: string;
  status: ScheduledTransferRunStatus;
  transactionId?: string;
  idempotencyKey: string;
  failureReason?: string;
};
```

Extend notification unions with scheduled-transfer values and `SCHEDULED_TRANSFER` source type.

- [ ] **Step 5: Add schema and query functions**

Add `scheduledTransferSchema` to `schemas.ts`:

```typescript
export const scheduledTransferSchema = z.object({
  fromAccountId: z.string().min(1, "Source account is required"),
  toAccountId: z.string().min(1, "Destination account is required"),
  amount,
  currency,
  description: z.string().max(500).optional(),
  reference: z.string().max(100).optional(),
  scheduleType: z.enum(["ONE_TIME", "RECURRING"]),
  frequency: z.enum(["WEEKLY", "BIWEEKLY", "MONTHLY"]).optional(),
  firstRunAt: z.string().min(1, "First run date is required"),
  endAt: z.string().optional()
}).superRefine((value, ctx) => {
  if (value.fromAccountId === value.toAccountId) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["toAccountId"], message: "Destination must be different" });
  }
  if (value.scheduleType === "RECURRING" && !value.frequency) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["frequency"], message: "Frequency is required" });
  }
  if (value.scheduleType === "ONE_TIME" && value.frequency) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["frequency"], message: "Frequency is only for recurring schedules" });
  }
});
```

Add query functions in `queries.ts`: `createScheduledTransfer`, `listScheduledTransfers`, `getScheduledTransfer`, `pauseScheduledTransfer`, `resumeScheduledTransfer`, `cancelScheduledTransfer`, and `listScheduledTransferRuns`.

- [ ] **Step 6: Add page and navigation**

Create `ScheduledTransfersPage.tsx` using existing `Panel`, `Field`, `Input`, `Select`, `Button`, `Badge`, `EmptyState`, and `ErrorNotice`. Use `listAccounts()` for account dropdowns and `availableBalance(account)` display. Use React Query invalidation after create/pause/resume/cancel.

Modify `CustomerLayout.tsx`:

```typescript
import { CalendarClock } from "lucide-react";

const navItems = [
  { to: "/", label: "Dashboard", icon: Gauge },
  { to: "/accounts", label: "Accounts", icon: WalletCards },
  { to: "/move-money", label: "Move Money", icon: ArrowLeftRight },
  { to: "/scheduled-transfers", label: "Scheduled", icon: CalendarClock },
  { to: "/transactions", label: "Transactions", icon: Banknote },
  { to: "/disputes", label: "Disputes", icon: CircleHelp },
  { to: "/statements", label: "Statements", icon: FileText },
  { to: "/notifications", label: "Notifications", icon: Bell }
];
```

Modify `App.tsx` to import and route `ScheduledTransfersPage`:

```tsx
<Route path="scheduled-transfers" element={<ScheduledTransfersPage />} />
```

- [ ] **Step 7: Run frontend verification**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: tests pass and build passes with only the known chunk-size warning.

- [ ] **Step 8: Commit Task 5**

```powershell
git add frontend/src/types.ts frontend/src/lib/schemas.ts frontend/src/lib/queries.ts frontend/src/components/CustomerLayout.tsx frontend/src/App.tsx frontend/src/pages/ScheduledTransfersPage.tsx frontend/src/lib/api.test.ts frontend/src/lib/schemas.test.ts frontend/src/test/app.component.test.tsx
git commit -m "feat(frontend): add scheduled transfers page"
```

## Task 6: End-To-End Verification And Documentation Refresh

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-06-30-scheduled-recurring-transfers-design.md` only if implementation reveals a necessary clarified behavior.

- [ ] **Step 1: Refresh README API and feature docs**

Add scheduled transfers to:

- Customer App feature list.
- Backend Services transaction-service feature list.
- Authenticated customer routes.
- API Surface Used By Frontend.
- Testing coverage list.

Document endpoints exactly:

```http
POST   /api/scheduled-transfers
GET    /api/scheduled-transfers?page=&size=&status=
GET    /api/scheduled-transfers/{scheduleId}
PATCH  /api/scheduled-transfers/{scheduleId}/pause
PATCH  /api/scheduled-transfers/{scheduleId}/resume
DELETE /api/scheduled-transfers/{scheduleId}
GET    /api/scheduled-transfers/{scheduleId}/runs?page=&size=
```

- [ ] **Step 2: Run focused backend checks**

Run:

```powershell
cd transaction-service
.\mvnw.cmd -q "-Dtest=ScheduledTransfer*Test" test
.\mvnw.cmd -q -DskipTests compile
```

Expected: scheduled-transfer tests pass and compile passes.

- [ ] **Step 3: Run account-service checks**

Run:

```powershell
cd account-service
.\mvnw.cmd -q test
```

Expected: all account-service tests pass.

- [ ] **Step 4: Run frontend checks**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: tests pass and build passes with only the known chunk-size warning.

- [ ] **Step 5: Check Git status and diff**

Run:

```powershell
git status --short
git diff --check
```

Expected: only intended files are modified before commit; `git diff --check` reports no whitespace errors.

- [ ] **Step 6: Commit Task 6**

```powershell
git add README.md docs/superpowers/specs/2026-06-30-scheduled-recurring-transfers-design.md
git commit -m "docs: document scheduled transfers"
```

- [ ] **Step 7: Final branch validation summary**

Run:

```powershell
git log --oneline origin/main..HEAD
git status -sb
```

Expected: branch is ahead of `origin/main` by the design, implementation, and documentation commits with a clean worktree.
