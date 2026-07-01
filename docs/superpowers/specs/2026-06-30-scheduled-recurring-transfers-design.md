# Scheduled And Recurring Transfers MVP Design

## 1. Objective

Add customer-owned scheduled transfers to the existing banking application. Customers can schedule one future transfer or create a recurring transfer between their existing accounts. Due schedules execute through the existing transaction-service transfer workflow so ledger journals, transaction history, limits, risk evaluation, audit logging, statements, and notifications remain consistent with immediate transfers.

This is a customer banking feature, not a new payment network. The MVP stays inside `transaction-service` and does not introduce a third service, external bank rails, bill presentment, or a payee directory.

## 2. Scope

### Included

- Customer creation of one-time scheduled transfers.
- Customer creation of recurring transfers with `WEEKLY`, `BIWEEKLY`, and `MONTHLY` frequencies.
- Customer listing and detail views for their own schedules.
- Customer pause, resume, and cancel actions.
- Durable execution of due schedules by a scheduled transaction-service worker.
- Execution attempt records with status, timestamps, idempotency key, linked transaction ID, and failure reason.
- Best-effort customer notifications for created, executed, failed, paused, resumed, and canceled schedules.
- Frontend customer route `/scheduled-transfers`.
- Focused admin visibility through existing transaction, audit, risk, investigation, and ledger surfaces after a schedule produces a transaction.
- Backend and frontend tests for ownership, validation, duplicate execution prevention, scheduling state transitions, and UI behavior.

### Excluded

- External bank transfers, ACH, cards, wires, or bill-pay rails.
- Payee or beneficiary management.
- Admin editing or manually running a customer's schedule.
- Automatic retry after a failed execution.
- PDF receipts.
- Time-zone preference management.
- New message broker, queue service, or third backend service.

## 3. Existing-System Constraints

`transaction-service` owns money movement, ledger posting, risk evaluation, transaction audit, and transaction history. Scheduled transfer execution must reuse the existing transfer service rather than duplicating transfer logic.

`account-service` owns customer accounts, account status, compatibility balance mirrors, and notifications. Scheduled transfer code may read account data through the existing resilient account client and may create notifications through the existing best-effort notification path, but it must not move account balances directly.

The current ledger design makes `transaction-service` the financial source of truth. Scheduled transfer execution must create normal transfer transactions so monthly statements, reconciliation, investigations, and risk controls observe the same data shape as immediate transfers.

## 4. Domain Model

### Scheduled Transfer

`scheduled_transfers` stores the customer-owned schedule definition.

- `schedule_id`: UUID primary key.
- `user_id`: authenticated customer that owns the schedule.
- `from_account_id`: source account ID.
- `to_account_id`: destination account ID.
- `amount`: transfer amount, positive, scale 2.
- `currency`: three-letter currency, MVP values match existing transfer validation.
- `description`: optional transfer description.
- `reference`: optional transfer reference.
- `schedule_type`: `ONE_TIME` or `RECURRING`.
- `frequency`: null for one-time, otherwise `WEEKLY`, `BIWEEKLY`, or `MONTHLY`.
- `next_run_at`: UTC instant when the next execution is due.
- `end_at`: optional UTC instant after which recurring execution stops.
- `status`: `ACTIVE`, `PAUSED`, `CANCELED`, or `COMPLETED`.
- `last_run_at`: nullable timestamp of the last attempted execution.
- `created_at`, `updated_at`.
- `version`: optimistic locking field.

One-time schedules move to `COMPLETED` after a successful execution. Recurring schedules remain `ACTIVE` and advance `next_run_at` until `end_at` is reached.

### Scheduled Transfer Run

`scheduled_transfer_runs` stores one row per execution attempt.

- `run_id`: UUID primary key.
- `schedule_id`: parent schedule.
- `scheduled_for`: UTC instant the run was due for.
- `started_at`: execution start timestamp.
- `completed_at`: execution end timestamp.
- `status`: `PROCESSING`, `COMPLETED`, `FAILED`, or `SKIPPED`.
- `transaction_id`: linked transaction when execution succeeds.
- `idempotency_key`: deterministic key used for the transfer call.
- `failure_reason`: sanitized error text for failed runs.

The pair `(schedule_id, scheduled_for)` must be unique. That uniqueness plus row-level claiming prevents duplicate execution by overlapping scheduler ticks.

## 5. API Design

All scheduled-transfer APIs require an authenticated user. Admin and internal-service roles can be added later only if a concrete operations workflow needs them.

```http
POST /api/scheduled-transfers
GET  /api/scheduled-transfers?page=&size=&status=
GET  /api/scheduled-transfers/{scheduleId}
PATCH /api/scheduled-transfers/{scheduleId}/pause
PATCH /api/scheduled-transfers/{scheduleId}/resume
DELETE /api/scheduled-transfers/{scheduleId}
GET  /api/scheduled-transfers/{scheduleId}/runs?page=&size=
```

Create request:

```json
{
  "fromAccountId": "101",
  "toAccountId": "102",
  "amount": 125.00,
  "currency": "USD",
  "description": "Monthly savings transfer",
  "reference": "SAVINGS",
  "scheduleType": "RECURRING",
  "frequency": "MONTHLY",
  "firstRunAt": "2026-07-31T09:00:00Z",
  "endAt": null
}
```

Validation rules:

- `fromAccountId`, `toAccountId`, `amount`, `currency`, `scheduleType`, and `firstRunAt` are required.
- Source and destination accounts must be different.
- `firstRunAt` must be in the future.
- `frequency` is required for `RECURRING` and forbidden for `ONE_TIME`.
- `endAt`, when present, must be after `firstRunAt`.
- The source account must belong to the authenticated user at creation time.
- Debit eligibility and sufficient funds are not guaranteed at creation time; they are rechecked at execution time by the existing transfer path.

## 6. Execution Flow

A scheduled worker in `transaction-service` runs at a configurable fixed delay, defaulting to one minute. Each tick finds due `ACTIVE` schedules whose `next_run_at` is less than or equal to now.

For each candidate schedule, the service claims the schedule with a database lock, creates a `PROCESSING` run row using `(schedule_id, scheduled_for)`, and then calls the existing transfer service with:

- the schedule's source account,
- destination account,
- amount,
- currency,
- description,
- reference,
- schedule owner as `userId`,
- deterministic idempotency key `scheduled-transfer:{scheduleId}:{scheduledFor}`.

On success:

- mark the run `COMPLETED`,
- store the created transaction ID,
- set `last_run_at`,
- advance `next_run_at` for recurring schedules,
- mark one-time schedules `COMPLETED`,
- create a best-effort success notification.

On failure:

- mark the run `FAILED`,
- store a sanitized failure reason,
- set `last_run_at`,
- keep recurring schedules `ACTIVE` and advance `next_run_at` unless `end_at` has passed,
- mark one-time schedules `COMPLETED` after the failed attempt so they do not retry automatically,
- create a best-effort failure notification.

Monthly recurrence uses the same day-of-month when possible. If the target month does not have that day, execution moves to the last day of that month. All persisted execution times are UTC.

## 7. Authorization And Ownership

Customers can only create and manage schedules where the source account belongs to them. Listing and detail endpoints filter by authenticated user. Pause, resume, cancel, and run-history endpoints must reject schedules owned by another user with `403`.

The execution worker runs as a system process but passes the schedule owner into the transfer service. That preserves existing ownership semantics, transaction history scoping, risk rules, and notifications.

## 8. Failure Handling

Failures must be visible and non-destructive.

- Frozen source account: run fails with a customer-visible reason from the transfer path.
- Insufficient available balance: run fails; no ledger journal posts.
- Destination account missing or invalid: run fails.
- Account-service outage: run fails for that attempt and records the service failure category.
- Duplicate worker attempt: unique run constraint or existing idempotent transfer result prevents duplicate money movement.
- Notification failure: logged only; it never changes schedule or transfer outcome.

No failed run is silently retried in the MVP. A later feature can add retry policy after the failure model is proven.

## 9. Frontend Design

Add customer route `/scheduled-transfers` and a navigation item labeled `Scheduled`.

The page has three working areas:

- Create schedule: source account, destination account, amount, currency, first run date/time, one-time or recurring mode, frequency, optional end date, description, and reference.
- Schedule list: active, paused, completed, and canceled schedules with next run, last run, amount, accounts, and action buttons.
- Run history: selected schedule's execution attempts with status, scheduled time, completion time, linked transaction ID, and failure reason.

Controls follow existing frontend patterns: React Query for data, React Hook Form plus Zod for validation, existing `Panel`, `Field`, `Input`, `Select`, `Button`, `Badge`, and `EmptyState` components. The UI should be compact and operational, matching the current banking console rather than adding a marketing-style page.

## 10. Notifications

Extend notification types to include:

- `SCHEDULED_TRANSFER_CREATED`
- `SCHEDULED_TRANSFER_EXECUTED`
- `SCHEDULED_TRANSFER_FAILED`
- `SCHEDULED_TRANSFER_PAUSED`
- `SCHEDULED_TRANSFER_RESUMED`
- `SCHEDULED_TRANSFER_CANCELED`

Notification source type can remain `TRANSACTION` only for completed runs with a linked transaction. For schedule lifecycle events, add `SCHEDULED_TRANSFER` as a source type in account-service and frontend types.

All notifications are best-effort and deduped with stable keys such as `scheduled-transfer:{scheduleId}:created` and `scheduled-transfer-run:{runId}:failed`.

## 11. Observability

Add metrics in transaction-service:

- schedules due per tick,
- schedules claimed per tick,
- scheduled transfer executions completed,
- scheduled transfer executions failed,
- execution latency,
- duplicate run prevention count.

Logs must include `scheduleId`, `runId`, `scheduledFor`, and `transactionId` when available. Logs must not include JWTs, authorization headers, or raw request secrets.

## 12. Test Strategy

Backend tests:

- Migration test for `scheduled_transfers` and `scheduled_transfer_runs`.
- Service tests for create validation, ownership checks, pause/resume/cancel transitions, and recurrence calculation.
- Scheduler tests for due schedule claiming, successful execution, failed execution, one-time completion, recurring next-run advancement, and duplicate prevention.
- Controller tests for authenticated access, owner-only access, pagination, and state actions.
- Notification tests verifying best-effort behavior does not change transfer outcomes.

Frontend tests:

- API client tests for scheduled-transfer endpoints.
- Zod schema tests for one-time and recurring validation.
- Component tests for create, list, pause, resume, cancel, and run-history flows.
- Navigation test verifying the customer shell links to `/scheduled-transfers`.

Verification commands should include:

```powershell
cd transaction-service
.\mvnw.cmd -q "-Dtest=ScheduledTransfer*Test" test
.\mvnw.cmd -q -DskipTests compile
```

```powershell
cd account-service
.\mvnw.cmd -q test
```

```powershell
cd frontend
npm test
npm run build
```

## 13. Acceptance Criteria

The MVP is complete when:

- A customer can create a future one-time transfer and see it execute into a normal completed transfer transaction.
- A customer can create a recurring weekly, biweekly, or monthly transfer and see the next run advance after each execution.
- A customer can pause, resume, and cancel their own schedules.
- A customer cannot view or modify another customer's schedules.
- Failed executions are recorded with a reason and do not disappear.
- Duplicate scheduler ticks cannot move money twice for the same scheduled run.
- Completed scheduled transfers appear in transaction history, ledger activity, monthly statements, audit/risk processing, and notifications through existing system paths.
- The feature passes focused backend tests, account-service tests, frontend tests, and frontend build.
