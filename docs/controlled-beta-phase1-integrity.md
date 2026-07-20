# Controlled Beta Phase 1: Financial Integrity and Evidence

This phase is limited to controlled synthetic-data beta operation. The optional
`financial-mcp-server` remains outside the beta runtime.

## Account lifecycle

- `POST /api/accounts` creates an account with zero posted, ledger, available,
  and pending balances. Money fields are not part of the accepted request.
- `PUT /api/accounts/{id}` is metadata-only. It accepts subtype metadata such as
  savings interest rate or credit-card limit/due date, but never balance fields.
- Customer hard deletion is removed. Closure is coordinated by
  `POST /api/controlled-beta/accounts/{accountId}/close`.
- Closure preserves the account row and all financial history. It requires zero
  posted, available, and pending balances; no placed holds; no active or paused
  scheduled transfers; no open or in-review disputes; and no active protective
  controls.
- The coordinator locks the authoritative ledger projection before checking
  balances, refuses closure while a projection delivery is pending, and closes
  the transaction ledger account in the same local transaction. Posting takes
  the same lock before reading ledger-account status, so posting and closure
  cannot cross in flight.
- Closed accounts reject balance operations, newer ledger projections, new
  holds, synthetic funding, and metadata/status changes. The admin console
  disables every mutation control on closed rows.

## Synthetic funding

`POST /api/controlled-beta/synthetic-funding` is operator-only and requires an
`Idempotency-Key`. It creates a normal ledger-authoritative deposit transaction
whose description and reference are visibly marked synthetic. The resulting
journal remains balanced and the operator action is audited.

The operation is disabled by default:

```properties
controlled-beta.synthetic-funding.enabled=false
```

It may be enabled only in an explicitly controlled synthetic-data environment.
The service fails closed whenever the active profile is `prod` or `production`,
even if the flag is set.

## Audit evidence

API identity comes from Spring Security `Authentication`. Caller-supplied
`X-User-Id` is ignored. The HTTP audit filter records the actual response status
after request processing. Method arguments and return values are not serialized;
only allowlisted event metadata is recorded.

Audit summary counters use one bounded aggregate query. Composite evidence
indexes cover the supported filters and descending event time. The console
renders timestamp values explicitly in UTC, while both Java services default
Jackson and Hibernate JDBC timestamps to `APP_TIME_ZONE=UTC`. Containerized
beta validation also sets `TZ=UTC` and `-Duser.timezone=UTC`.

Forwarded IP data is ignored by default. It is considered only when both settings
are explicitly configured and the direct peer address is allowlisted:

```properties
audit.trusted-proxy.enabled=true
audit.trusted-proxy.addresses=10.0.0.5,10.0.0.6
```

## Worker recovery

- Ledger projection dispatch claims due rows and owns the database transaction
  at the scheduled entry point, so delivery and retry state remain persisted
  when the scheduler invokes the worker.
- Notification dispatch claims only notifications that do not already have a
  provider receipt, isolates each item failure, bounds retries through receipt
  state, exposes backlog/oldest-age/terminal-failure metrics, and provides an
  admin replay endpoint.
- Scheduled-transfer execution checks every transaction status for the
  deterministic idempotency key. Completed and terminal transactions reconcile
  without moving money again. A stale `PROCESSING` run with no transaction is
  released after `scheduled-transfer.processing-stale-seconds` (default 300) and
  safely retried under the schedule row lock. Operators can trigger bounded
  recovery through the admin recovery endpoint.

## Secret handling

Runtime `.env.dev`, `.env.staging`, and `.env.prod` files under
`financial-mcp-server` are no longer tracked. Only `.env.example` remains, with
non-secret placeholders. Repository ignore rules reject runtime environment
files, CI checks that none are tracked, and gitleaks scans the complete checkout
with full history.

Code changes do not remove secrets from existing Git history and do not rotate
external credentials. Any value ever used outside local examples must be rotated,
and history cleanup requires a separately approved repository operation.
