# Immutable ledger cutover runbook

This runbook controls the MVP cutover from legacy account-service balance mutation to transaction-service ledger authority.

## Maintenance preconditions

Only run cutover during an approved maintenance window.

- Deploy dormant ledger schema and APIs with `ledger.authoritative=false`.
- Pause customer money movement at the edge or operations layer.
- Confirm account-service and transaction-service health checks are `UP`.
- Confirm there are no unresolved account-service debit holds. Legacy mirrors must have `ledgerBalance == availableBalance` for every account imported into the ledger.
- Confirm transaction-service has no `PENDING` or `PROCESSING` transactions.
- Confirm operators have admin or internal-service authority for transaction-service admin APIs.

## Evidence capture

Capture these artifacts before and after cutover:

- Git SHA and container image digests for account-service, transaction-service, and frontend.
- `GET /actuator/health` for both services.
- Docker compose service status and database migration versions.
- Account-service account export count and currency distribution.
- Transaction-service ledger account count, projection count, and open reconciliation exception count.
- Prometheus snapshots for `ledger_pending_journals_count`, `ledger_projection_outbox_backlog`, `ledger_suspense_balance`, and `ledger_projection_invariant_failures_total`.

## Bootstrap preflight

Before every bootstrap attempt, call the admin-only read-only preflight while ledger authority is still disabled:

```http
GET /api/admin/ledger/bootstrap/preflight?maintenanceMode=true
Authorization: Bearer <admin-token>
```

The response reports whether maintenance was explicitly confirmed, row counts, required and missing system accounts, supported currencies, and blockers. The safe default system currencies are `USD,EUR,GBP,INR`; override them only with the reviewed `LEDGER_BOOTSTRAP_SYSTEM_CURRENCIES` deployment setting. Do not continue unless `ready=true`. A preflight never writes ledger data and does not reserve the result, so keep money movement paused until bootstrap and reconciliation finish.

## Bootstrap command

Run bootstrap only after preconditions are met:

```http
POST /api/admin/ledger/bootstrap
Content-Type: application/json

{
  "enabled": true,
  "maintenanceMode": true,
  "businessDate": "2026-06-26"
}
```

Expected result:

- The response contains a durable `runId`; retain it with the deployment evidence.
- Customer ledger accounts imported or reused idempotently.
- Clearing, suspense, and fee system accounts exist for every configured system currency, including INR by default.
- Clearing, suspense, and fee system accounts exist for every imported currency.
- Opening journals are balanced and posted.
- Projection parity matches account-service mirrors.

Every attempt writes a `ledger_bootstrap_runs` audit row with actor, mode, business date, outcome, counts/currencies, and a sanitized failure reason. If the response is `409`, stop cutover and resolve the blocker before retrying. Retrying is idempotent, but operators must still re-run preflight and retain the new run evidence.

## Explicit fresh-database startup mode

A brand-new financial database may be bootstrapped during transaction-service startup only in an isolated maintenance deployment. The mode is disabled by default and fails application startup unless all gates agree.

Set exactly:

```properties
ledger.authoritative=false
ledger.bootstrap.startup.enabled=true
ledger.bootstrap.startup.maintenance-mode=true
ledger.bootstrap.system-currencies=USD,EUR,GBP,INR
```

Operator sequence:

1. Provision an empty transaction-service database and keep customer traffic and scheduled-transfer workers disabled at the edge/deployment layer.
2. Start transaction-service once with the four settings above. Flyway creates the schema and audit table; the startup coordinator verifies the database is fresh before seeding system accounts.
3. Capture the successful startup log and `ledger_bootstrap_runs` evidence. If startup fails, do not weaken a gate; inspect the persisted failure evidence and provision or clean the intended disposable database through the approved database workflow.
4. Stop the bootstrap deployment and restart with `ledger.bootstrap.startup.enabled=false` and `ledger.bootstrap.startup.maintenance-mode=false`.
5. Run reconciliation and the remaining cutover gates before enabling `ledger.authoritative=true`.

This mode never imports or fabricates customer balances on a non-fresh database, never activates scheduled transfers, and never creates customer money movement. Reusing it on a populated database or combining it with authoritative ledger mode fails closed.

## Zero-critical-exception gate

Run daily reconciliation immediately after bootstrap:

```http
POST /api/admin/reconciliation/runs
Content-Type: application/json

{
  "businessDate": "2026-06-26"
}
```

Cutover may proceed only when there are zero unexplained critical exceptions. Resolved or waived exceptions must include operator notes and evidence. Do not enable ledger authority with open critical reconciliation exceptions.

## Enable ledger authority

After the zero-critical-exception gate, deploy transaction-service with:

```properties
ledger.authoritative=true
```

Then resume money movement and monitor:

- `LedgerProjectionInvariantFailureCritical`
- `LedgerSuspenseBalanceNonZero`
- `LedgerStalePendingJournals`
- `LedgerProjectionOutboxBacklogSustained`
- `LedgerCriticalReconciliationExceptions`

## Rollback boundary

Rollback to `ledger.authoritative=false` is permitted only before any authoritative post-cutover journals are accepted.

Before this boundary, rollback means:

- Keep ledger tables for investigation.
- Return transaction-service to `ledger.authoritative=false`.
- Keep account-service as the balance mutation authority.
- Re-run bootstrap later after blockers are resolved.

## After ledger authority is enabled

After ledger authority is enabled and new authoritative journals post, do not roll back to mutable account-service balance authority.

All corrections after this boundary must be forward fixes or compensating entries:

- Never edit posted journals, postings, state events, reconciliation records, or generated statements.
- Use reversal or correction journals for financial repair.
- Use reconciliation exception notes to link operator decisions to evidence.
- Keep account-service as the identity/status service and ledger projection mirror.
