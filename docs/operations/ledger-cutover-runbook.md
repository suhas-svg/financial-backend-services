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

- Customer ledger accounts imported or reused idempotently.
- Clearing, suspense, and fee system accounts exist for every imported currency.
- Opening journals are balanced and posted.
- Projection parity matches account-service mirrors.

If the response is `409`, stop cutover and resolve the blocker before retrying.

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
