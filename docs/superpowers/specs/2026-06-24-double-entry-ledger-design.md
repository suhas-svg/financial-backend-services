# Immutable Double-Entry Ledger and Reconciliation Design

**Status:** Approved design, pending written-spec review
**Date:** 2026-06-24
**Repository:** `suhas-svg/financial-backend-services`
**Branch:** `codex/double-entry-ledger-mvp`
**Authoritative baseline:** `origin/main` at `e249cac`

## 1. Objective

Add an immutable, currency-safe double-entry ledger to the existing two-service application without introducing a third service. `transaction-service` becomes the financial source of truth for journal posting and balance projections. `account-service` continues to own customer/account identity, lifecycle status, and account currency, while retaining versioned balance mirrors for backward compatibility.

The MVP includes immutable journals, balanced postings, customer and system ledger accounts, idempotent posting, pending/posted/failed/reversed lifecycle states, compensating corrections, balance projections, daily reconciliation, an admin exception queue, monthly customer statements, and audit links to transactions, disputes, risk cases, and audit events.

## 2. Scope and explicit exclusions

### Included

- Customer, clearing, suspense, and fee ledger accounts.
- One immutable ISO 4217 currency per customer or system ledger account.
- Pending debit reservations and posted financial entries.
- Posted, pending, and available balance projections.
- Existing deposit, withdrawal, transfer, fee, and reversal workflows.
- Immutable monthly statement snapshots with in-app viewing and CSV export.
- Scheduled and manually triggered reconciliation.
- Versioned, retryable compatibility-mirror delivery to `account-service`.
- Customer and admin frontend workflows.
- PostgreSQL migrations, automated tests, Docker verification, documentation, and draft PR publication.

### Excluded

- Foreign-exchange conversion or cross-currency journals.
- A third ledger or reconciliation service.
- Email, postal, or PDF statement delivery.
- External bank, card-network, or general-ledger reconciliation feeds.
- Editing, deleting, or replacing posted entries.
- Indefinite shadow-ledger or dual-authority operation.

## 3. Existing-system constraints

The current system stores mutable `balance`, `ledger_balance`, and `available_balance` columns plus debit holds in `account-service`. `transaction-service` orchestrates money movement and calls idempotent account balance/hold endpoints. It stores workflow transactions, reversals, audit events, risk alerts/cases, and disputes.

The ledger design must preserve:

- Existing public account and transaction routes and response fields.
- Existing customer and admin console behavior.
- Account freeze semantics: debits are blocked while credits remain allowed.
- Existing transaction IDs, dispute/risk links, idempotency headers, and reversal entry points.
- Existing `ROLE_USER`, `ROLE_ADMIN`, and `ROLE_INTERNAL_SERVICE` authorization model.

`transactions` remains the business workflow record. It is not the accounting journal and is not used to calculate authoritative balances after cutover.

## 4. Architecture decision

### Selected: ledger authority with a compatibility mirror

`transaction-service` owns:

- Ledger accounts and system accounts.
- Journal commands, postings, lifecycle events, and reversals.
- Pending, posted, and available projections.
- Idempotency claims and request fingerprints.
- Mirror-delivery outbox.
- Reconciliation runs and exceptions.
- Monthly statement snapshots.

`account-service` owns:

- User/customer identity and authentication.
- Account identity, type, owner, lifecycle status, and immutable currency.
- Versioned balance mirrors used by existing account responses.
- Account freeze/unfreeze and customer notifications.

The mirror is eventually consistent and never authorizes a posting. New UI paths read authoritative projections from `transaction-service`. Existing clients continue to receive `balance`, `ledgerBalance`, and `availableBalance` from `account-service`; reconciliation measures and reports mirror lag or drift.

This avoids a synchronous `account-service -> transaction-service` dependency while establishing one financial source of truth.

## 5. Domain model

### 5.1 Ledger account

`ledger_accounts` contains:

- Stable UUID `ledger_account_id`.
- `account_kind`: `CUSTOMER`, `CLEARING`, `SUSPENSE`, or `FEE`.
- Immutable uppercase `currency`.
- Optional `external_account_id` for the `account-service` account ID.
- Optional `owner_id` for authorization and statement grouping.
- Lifecycle state: `ACTIVE` or `CLOSED`.
- Creation metadata and an optimistic-lock version.

Constraints:

- One customer ledger account per external account ID.
- One active clearing, suspense, and fee account per currency.
- Customer ledger account currency must equal the owning account's currency.
- Currency and account kind cannot change after creation.
- Closing an account prevents new postings but does not alter history.

### 5.2 Journal transaction

`journal_transactions` is an immutable posting envelope containing:

- UUID `journal_id` and human-readable journal reference.
- Journal type such as `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `FEE`, `REVERSAL`, `CORRECTION`, or `OPENING_BALANCE`.
- Currency, effective date, description, correlation ID, creator, and creation timestamp.
- Scoped idempotency key and canonical request fingerprint.
- Optional `reversal_of_journal_id` for a compensating journal.

Journal content never changes after insertion. Effective lifecycle state comes from append-only `journal_state_events`:

- `PENDING -> POSTED`
- `PENDING -> FAILED`
- `POSTED -> REVERSED`

`REVERSED` requires a posted compensating journal that swaps every original debit and credit. The original journal and postings remain unchanged.

### 5.3 Journal posting

`journal_postings` contains immutable positive amounts with explicit `DEBIT` or `CREDIT` direction, ledger-account ID, currency, sequence, and optional customer-facing memo.

Posting invariants:

- At least two postings per financial journal.
- Every posting amount is greater than zero and uses the journal currency.
- Total debits equal total credits exactly for each journal.
- A journal contains only one currency.
- A posting cannot target a closed account.
- Journal headers, postings, and state events cannot be updated or deleted.

Application validation provides useful errors; PostgreSQL constraints, deferred constraint triggers, and immutability triggers provide the final enforcement boundary.

### 5.4 Standard posting templates

| Operation | Debit | Credit |
|---|---|---|
| Deposit | Currency clearing | Customer |
| Withdrawal | Customer | Currency clearing |
| Transfer | Source customer | Destination customer |
| Fee | Customer | Currency fee |
| Reversal/correction | Original credits | Original debits |
| Opening balance | Currency clearing | Customer with positive opening balance |

Negative customer opening balances are outside the MVP because existing account validation requires non-negative balances.

### 5.5 Balance projection

`ledger_balance_projections` stores a lockable, versioned projection per ledger account:

- `posted_balance`: sum of posted journal effects.
- `pending_balance`: signed net pending activity.
- `pending_debits`: pending debits reserved against spendable funds.
- `pending_credits`: visible incoming pending value.
- `available_balance`: `posted_balance - pending_debits`.
- Last applied event sequence, projection version, and update timestamp.

Pending credits do not increase available balance until posted. Pending debits reduce available balance immediately. Projection mutation and state-event insertion occur in the same database transaction.

Projection rows are locked in deterministic ledger-account-ID order to prevent transfer deadlocks. A debit is rejected when the post-operation available balance would be negative.

## 6. Posting lifecycle

### 6.1 Command intake

1. Authenticate the caller and obtain the user/role from the JWT, never from request identity fields.
2. Normalize and validate the `Idempotency-Key`.
3. Create or read an idempotency claim scoped by actor, operation type, and key.
4. Compare a canonical request fingerprint on replay. A different payload using the same key returns `409 Conflict`.
5. Resolve account identity/status/currency through the existing secured `account-service` integration.
6. Validate ownership, active debit status, same-currency accounts, amount, limits, and system-account availability.

### 6.2 Pending creation

Within one PostgreSQL transaction:

- Lock affected projections in deterministic order.
- Recheck available funds.
- Insert the immutable journal and balanced postings.
- Append `PENDING`.
- Apply pending projection effects.
- Persist the transaction workflow linkage and audit correlation.

No remote service call occurs while projection locks are held.

### 6.3 Completion

Within a second PostgreSQL transaction:

- Lock the journal and affected projections.
- Ensure the latest state is `PENDING`.
- Append `POSTED` or `FAILED`.
- For `POSTED`, move pending effects into posted balances.
- For `FAILED`, release all pending effects.
- Update the existing workflow transaction status and processing state.
- Enqueue compatibility-mirror messages and audit/risk evaluation work.

Repeated completion commands are idempotent. Illegal or conflicting transitions return `409 Conflict` and create an operational audit event.

### 6.4 Correction and reversal

The existing reversal endpoint remains backward compatible. It creates a new idempotent compensating journal linked to the original journal and transaction. After the compensation posts, a `REVERSED` event is appended to the original journal and existing reversal linkage fields are updated.

Partial edits are prohibited. Administrative corrections use the same compensating mechanism and require a reason, actor, correlation ID, and privileged role.

## 7. Failure handling

| Failure | Required behavior |
|---|---|
| Validation or ownership failure | Reject before financial postings; persist the idempotent failed result where applicable. |
| Account-service unavailable before journal creation | Return retryable failure; do not create a posted journal. |
| Duplicate request | Return the recorded outcome; reject mismatched payload fingerprints. |
| Concurrent debit | Row locks serialize availability checks; only affordable debits succeed. |
| Posting transaction rollback | No journal, state event, or projection effect survives partially. |
| Mirror delivery failure | Retry from outbox with backoff; posted journal remains valid. |
| Stale pending journal | Reconciliation opens an exception; no silent auto-post. A safe retry may explicitly complete or fail it. |
| Automatic compensation succeeds | Post compensating journal and preserve both histories. |
| Automatic compensation cannot complete | Post to suspense where a balanced entry is possible and open a critical exception. |
| Projection invariant failure | Roll back the command, emit a critical metric, and open/reuse an exception. |

No catch block may convert a failed database transaction into a successful API response. Manual intervention always operates through explicit state events or compensating journals.

## 8. Compatibility mirror

`ledger_projection_outbox` records projection changes transactionally with journal completion. A scheduled dispatcher sends versioned updates to a new internal `account-service` endpoint.

Mirror request fields:

- External account ID.
- Posted, pending, and available values.
- Currency.
- Monotonic projection version.
- Source journal/event ID and update timestamp.

`account-service` applies an update only when its version is newer. Exact replays succeed without mutation. Currency or same-version/value conflicts are rejected and audited. The legacy `balance` alias continues to equal posted/ledger balance.

Mirror lag is observable and does not affect authorization or available-funds decisions. Daily reconciliation compares mirror version and amounts against authoritative projections.

## 9. API design

### 9.1 Existing APIs

The following remain source-compatible:

- `POST /api/transactions/deposit`
- `POST /api/transactions/withdraw`
- `POST /api/transactions/transfer`
- `POST /api/transactions/{transactionId}/reverse`
- Existing transaction history/search/detail/statistics routes.
- Existing public account CRUD/status responses, including balance fields.

Responses may add journal and projection links but will not remove or rename fields.

### 9.2 Customer ledger APIs

- `GET /api/ledger/accounts`
- `GET /api/ledger/accounts/{externalAccountId}/balance`
- `POST /api/ledger/accounts/balances:batch`
- `GET /api/ledger/journals/{journalId}`
- `GET /api/statements?year=`
- `GET /api/statements/{statementId}`
- `GET /api/statements/{statementId}/export.csv`

Customers see only their accounts and customer-safe posting descriptions. System ledger account IDs, internal exception notes, and other customers' data are redacted.

### 9.3 Admin reconciliation APIs

- `GET /api/admin/reconciliation/runs`
- `POST /api/admin/reconciliation/runs`
- `GET /api/admin/reconciliation/runs/{runId}`
- `GET /api/admin/reconciliation/exceptions`
- `GET /api/admin/reconciliation/exceptions/{exceptionId}`
- `PATCH /api/admin/reconciliation/exceptions/{exceptionId}/assignment`
- `PATCH /api/admin/reconciliation/exceptions/{exceptionId}/status`
- `POST /api/admin/reconciliation/exceptions/{exceptionId}/notes`

Mutation requests use optimistic versions to prevent administrators overwriting one another's work.

### 9.4 Internal account API

- `PUT /api/internal/accounts/{id}/ledger-projection`

Legacy internal balance/hold endpoints remain during cutover but are no longer called after ledger authority is enabled. Removal is a later, separately reviewed change.

## 10. Authorization and data exposure

- Customer endpoints require authentication and resolve ownership server-side.
- Cross-customer, system-account, reconciliation, suspense, and fee views require `ROLE_ADMIN` or `ROLE_INTERNAL_SERVICE` as appropriate.
- Reconciliation mutations and manual runs require `ROLE_ADMIN`.
- Mirror updates require `ROLE_INTERNAL_SERVICE`.
- Internal system accounts never appear in customer account lists.
- CSV exports use the same authorization and filters as the corresponding JSON endpoint.
- Logs and audit metadata exclude JWTs, authorization headers, credentials, raw request bodies, and database connection data.

Every privileged action records actor, role, action, target, outcome, correlation ID, and timestamp.

## 11. Audit linkage

`journal_links` stores typed immutable links from a journal to:

- `TRANSACTION`
- `DISPUTE`
- `RISK_CASE`
- `AUDIT_EVENT`
- `REVERSAL`
- `RECONCILIATION_EXCEPTION`

The table uses `(journal_id, link_type, external_id)` uniqueness. Existing investigation timelines add journal and reconciliation items through focused queries rather than copying ledger data into risk/dispute tables.

## 12. Reconciliation

### 12.1 Scheduling and execution

A scheduler in `transaction-service` starts the daily run shortly after the UTC date boundary. Administrators can request an additional run. PostgreSQL advisory locking ensures only one active run for a business date and reconciliation type.

Runs are immutable summaries with append-only check results. Reruns create new run IDs.

### 12.2 Checks

- Journal debit/credit balance by currency.
- Journal and posting currency consistency.
- Legal lifecycle transitions and required reversal links.
- Projection recomputation versus stored projection.
- Compatibility mirror version and amount drift.
- Missing transaction, dispute, risk-case, or audit linkage.
- Stale pending journals beyond the configured threshold.
- Failed or incomplete compensation.
- Non-zero suspense balances.
- Missing currency-specific clearing, suspense, or fee accounts.
- Statement closing balance versus authoritative projection snapshot.

### 12.3 Exception queue

`reconciliation_exceptions` uses a stable fingerprint to deduplicate the same unresolved discrepancy across runs. States are `OPEN`, `ACKNOWLEDGED`, `IN_PROGRESS`, `RESOLVED`, and `WAIVED`. Resolution and waiver require a note; waiver additionally requires an admin reason and remains auditable.

Severity is deterministic. Unbalanced journals, unexplained projection drift, and failed compensation are critical. Mirror lag within the retry window is informational and becomes warning/high severity only after configured thresholds.

Resolving an exception never edits ledger history. Financial repair requires a compensating journal; metadata repair uses an append-only link or audit event.

## 13. Monthly statements

On the first day of each UTC month, `transaction-service` closes the preceding period for each eligible customer ledger account. Generation is idempotent by `(ledger_account_id, period_start, period_end)`.

Each immutable statement stores:

- Account identity snapshot and currency.
- UTC period boundaries.
- Opening, total debits, total credits, and closing posted balance.
- Generation timestamp and source event watermark.
- Ordered customer-safe statement lines linked to posted journals.

Pending items are excluded and remain visible in the live account view. Corrections posted after close appear in the next statement with original-journal linkage. Generated statements are never regenerated in place; an operationally necessary replacement receives a new version and preserves the superseded version.

The MVP provides in-app viewing and CSV export. PDF delivery is excluded.

## 14. Frontend changes

### Customer console

- Dashboard and account pages batch-load authoritative projections.
- Display available balance first, then posted and pending balances.
- Preserve graceful rendering of legacy mirror fields during deployment skew.
- Transaction detail shows journal state, posting date, and reversal/correction linkage without internal posting details.
- Add `/statements` with period list, statement detail, totals, lines, and CSV export.
- Clearly label pending credits as unavailable and pending debits as reserved.

### Admin console

- Add `/admin/reconciliation` navigation and route.
- Provide run summary cards, filters, sortable exception table, detail panel, links to investigations, assignment, notes, status changes, and manual-run control.
- Add journal/reconciliation links to transaction and investigation details.
- Display suspense totals and oldest pending age in operational monitoring.

TanStack Query keys are separated by projection, statement, run, and exception filters. Mutations invalidate only the affected queue/detail/summary queries.

## 15. Database migrations and bootstrap

### account-service

Add a new Flyway migration after V5 to:

- Add `currency CHAR(3) NOT NULL DEFAULT 'USD'` with uppercase/length validation.
- Add `ledger_projection_version`, `pending_balance`, and mirror synchronization metadata.
- Backfill existing accounts as USD.
- Add database protection against currency changes after creation.

New account creation defaults to USD for backward-compatible requests. Explicit supported currency input is accepted once system accounts for that currency exist.

### transaction-service

Add migrations after V11 in dependency order:

1. Ledger accounts and system-account uniqueness.
2. Journals, postings, state events, idempotency, links, and immutability triggers.
3. Projections and outbox.
4. Reconciliation runs/exceptions/notes.
5. Statement periods/lines.
6. Required indexes, constraints, and operational seed configuration.

The migrations also correct the existing transaction processing-state constraint so deployed PostgreSQL accepts all Java enum states, including hold states.

### Bootstrap and cutover

1. Deploy dormant schema and internal APIs with ledger authority disabled.
2. Pause money movement for a bounded maintenance window.
3. Verify there are no unresolved legacy holds or processing transactions.
4. Import account identity/status/currency from secured internal account APIs.
5. Seed clearing, suspense, and fee accounts for supported currencies.
6. Create idempotent balanced opening journals from legacy posted balances.
7. Recompute and compare all projections with account mirrors.
8. Enable ledger-authoritative posting and mirror dispatch.
9. Run reconciliation and require zero unexplained critical exceptions.
10. Enable frontend routes and resume money movement.

Rollback is permitted before step 8. After authoritative journals post, rollback must preserve ledger authority; defects are repaired through compensating entries or forward fixes.

## 16. Observability

### Metrics

- Posting command count/latency/outcome by operation and currency.
- Idempotent replay and payload-conflict counts.
- Pending journal count and oldest age.
- Projection lock contention and invariant failures.
- Outbox backlog, retry count, oldest age, and mirror drift.
- Reconciliation duration, checks, exceptions, and severity counts.
- Suspense balance by currency.
- Statement generation duration and failure count.

Currency and operation type are bounded tags. Journal, transaction, account, and customer IDs belong in structured logs/traces, not metric labels.

### Logs and tracing

Use correlation, transaction, and journal IDs across controller, posting, outbox, reconciliation, and statement work. Log state transitions and outcomes, not sensitive payloads. Existing audit storage records business/security events; operational logs remain separate.

### Alerts

Alert on unbalanced-journal attempts, projection invariant failures, critical reconciliation exceptions, non-zero suspense, stale pending journals, and sustained mirror backlog.

## 17. Test strategy

### Backend unit and domain tests

- Debit/credit balancing and currency validation.
- State-machine transitions and immutable correction behavior.
- Projection semantics for pending debits/credits, posting, failure, and reversal.
- Idempotency fingerprint replay/conflict behavior.
- Ownership, frozen-account, and insufficient-funds enforcement.
- Reconciliation fingerprint/severity/lifecycle behavior.
- Statement opening/closing balance and late correction behavior.

### PostgreSQL integration tests

Use Testcontainers rather than H2 for ledger-critical coverage:

- Flyway migration from the existing schema.
- Deferred balance constraints and immutability triggers.
- Concurrent idempotent posting.
- Concurrent debits against the same available balance.
- Deterministic transfer locking.
- Projection/state-event atomicity.
- Outbox replay and version ordering.
- Reconciliation recomputation and deduplication.

### Service and API tests

- Existing endpoint regression tests and response compatibility.
- Controller ownership and role matrices.
- Account-service outage and timeout behavior.
- Mirror retry, stale version, and currency-conflict behavior.
- Compensation and suspense paths.
- CSV authorization, escaping, and content.

### Frontend tests

- Projection loading and legacy fallback rendering.
- Pending/posted/available labels and totals.
- Statement list/detail/export.
- Admin reconciliation filters, detail, assignment, notes, status, and manual runs.
- Existing customer/admin component regression suite.

### End-to-end and live verification

- Full frontend unit tests, lint, and production build.
- Both Maven test suites.
- Handoff protocol tests.
- Docker Compose rebuild from clean volumes to prove Flyway behavior.
- Live register/login/account/deposit/withdraw/transfer/reversal flows.
- Customer balance and statement checks.
- Admin reconciliation and exception workflow.
- Playwright customer/admin journeys.
- Final reconciliation with zero unexplained critical differences.

## 18. Documentation and delivery gates

Update:

- Root README architecture, API, and local verification sections.
- Service READMEs and database migration guidance.
- Docker configuration and environment-variable reference.
- Operational runbook for cutover, reconciliation, suspense, mirror recovery, and statement regeneration policy.
- Frontend route documentation.

Implementation begins only after this written specification is reviewed. The subsequent implementation plan must use test-driven, bite-sized tasks and repository checkpoints after each major task. Each checkpoint records validation and is intentionally committed and pushed. Final delivery requires a reviewed draft PR from `codex/double-entry-ledger-mvp`, complete CI evidence, Docker/live evidence, migration notes, and explicit disclosure of any remaining limitations.

## 19. Acceptance criteria

The MVP is complete when:

- Every posted financial transaction has a balanced, immutable journal.
- Database enforcement prevents mutation/deletion of ledger history.
- Concurrent duplicate or overspending requests cannot double-post or overspend.
- Currency mismatch and cross-currency posting are rejected.
- Posted, pending, and available projections follow the approved semantics.
- Reversals/corrections use compensating journals only.
- Existing account/transaction APIs and customer/admin workflows remain compatible.
- Daily reconciliation populates a usable, authorized admin exception queue.
- Customer monthly statements are immutable, viewable, and exportable as CSV.
- Journals link to relevant transaction, dispute, risk, audit, reversal, and reconciliation records.
- Mirror failure cannot invalidate a posted journal and is observable/recoverable.
- Migrations pass on a clean and upgraded PostgreSQL database.
- Automated, Docker, live API, and browser verification pass with no unexplained critical reconciliation differences.
