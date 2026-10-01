# Background jobs and multiple replicas

The Helm chart runs `transaction-service` with several replicas (`replicaCount: 3` in production), and
every replica runs every `@Scheduled` job. This page records, per job, why that is safe and what makes
it so. Read it before adding a scheduled job.

## The rule

Decide what the job's state is, then pick the matching tool. A blanket cluster-wide lock is wrong for
some jobs and unnecessary for most.

| The job works on... | Use |
| --- | --- |
| Independent database rows (a queue, due items, stale items) | Claim rows with `FOR UPDATE SKIP LOCKED`, so replicas split the work |
| Each replica's own in-memory state (gauges, counters) | Nothing. It must run on every replica |
| Idempotent remote or database effects | Nothing, if the effect is idempotent by key |

Claim a small batch (or one row) per transaction. Row locks are held until the transaction ends, so a
long transaction blocks any user request that touches the same row.

## Per-job audit

| Job | State it works on | Multi-replica behaviour |
| --- | --- | --- |
| `ScheduledTransferScheduler` | `scheduled_transfers` rows | Rows claimed with `SKIP LOCKED` (`claimDueActive`). A unique `(schedule, scheduledFor)` run row and the idempotency key already prevent a duplicate run |
| `TransactionServiceImpl.processPendingTransactions` (stale-transaction recovery) | `transactions` rows stuck in `PROCESSING` | Rows claimed with `SKIP LOCKED` (`claimStaleProcessing`, 100 per run). Recovery no longer overwrites a transaction another worker is completing |
| `OutcomeScenarioMonitor` | `outcome_scenarios` rows | One scenario per transaction, claimed with `SKIP LOCKED`. A failing scenario rolls back alone and is not retried in the same run |
| `LedgerProjectionOutboxDispatcher`, `FinancialEvidenceOutboxDispatcher` | outbox rows | Already `FOR UPDATE SKIP LOCKED` |
| `FinancialOperationsCoordinator` | daily/monthly runs | Already uses a claim lease (`financial-operations.claim-lease-seconds`) |
| `OutcomeNotificationDispatcher` | notification deliveries | Correct: each delivery is re-read under a row lock and re-checked (`DELIVERED`, `nextAttemptAt`) before sending. Replicas contend but never double-send |
| `SpendingLimitReservationSagaCoordinator.reconcileStaleClaims` | idempotency claims | Idempotent downstream: the account service locks the reservation and treats a repeated same-target transition as a no-op, and replicas reach the same decision from the same transaction status. Concurrent runs cost duplicate calls, not wrong state |
| `AuditService.cleanupOldAuditLogs` | audit rows older than the retention window | Idempotent bounded `DELETE`. Concurrent runs are harmless |
| `AlertingService` (two jobs), `ScheduledMetricsService` | **per-replica** in-memory counters and gauges | Must run on every replica. A cluster-wide lock would leave every other replica's gauges stale |

`ScheduledMetricsService.resetDailyCounters` also writes a `DAILY_RESET` audit event, so an audit log
from N replicas shows N of them at midnight. That is expected, not a duplicate-processing bug.

## Verifying

`BackgroundJobClaimIntegrationTest` runs against a real PostgreSQL. Each test opens a claim in one
transaction, keeps it open as another replica mid-run would, then claims from a second transaction and
asserts the second returns promptly with a disjoint set of rows. It also runs two
`OutcomeScenarioMonitor` instances at once and asserts every scenario is evaluated exactly once.

To run it without Docker/Testcontainers, point it at a scratch database (never a real one; the test
truncates the tables it uses):

```bash
LEDGER_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/scratch \
LEDGER_TEST_DB_USER=postgres LEDGER_TEST_DB_PASSWORD=... \
./mvnw -Dtest=BackgroundJobClaimIntegrationTest test
```
