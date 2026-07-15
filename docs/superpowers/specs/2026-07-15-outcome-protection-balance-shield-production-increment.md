# Outcome Protection / Balance Shield production increment

**Status:** Implementation baseline
**Date:** 2026-07-15
**Branch:** `codex/outcome-protection-balance-shield`
**Authoritative baseline:** `origin/main` at `1457966f6624493510c3639437c87bea1aae5ae4`
**Builds on:** Money Debugger MVP from PR #37

## 1. Objective

Close four deliberately deferred production boundaries in the smallest coherent vertical slice: prove INR end to end, make active scheduled transfers an explicit authoritative forecast input, turn fresh-state divergence into auditable and idempotent customer warnings, and make fresh-database ledger bootstrap an explicit fail-closed deployment workflow.

Outcome Protection remains deterministic, read-only, and preview-only. This increment does not execute money movement, activate a guardrail, change a schedule, bypass transfer authorization, or treat customer acknowledgement as consent to move funds.

## 2. Current-state findings

The merged MVP already persists immutable scenarios/results, reads authoritative ledger projections, expands active scheduled transfers, exposes refresh and warning-acknowledgement routes, and emits best-effort account-service notifications. The production gaps are narrower but material:

- supported-currency validation, frontend schemas, account creation, and default ledger system-account seeding stop at USD/EUR/GBP, so INR cannot be demonstrated end to end;
- schedule expansion is private to the scenario service and lacks focused ownership/status/currency/time-zone/horizon tests and rich causal snapshot metadata;
- refresh does not persist evidence for every evaluation, does not expose the warning event needed by the acknowledgement UI, and can permanently skip notification retry after a best-effort delivery failure;
- a fresh transaction database can start without the system accounts required by ledger posting, while bootstrap is a manual maintenance-gated call with no preflight response or durable run evidence.

## 3. Ownership and boundaries

- `transaction-service` owns ledger truth, scheduled-transfer truth, scenario snapshots/simulations, divergence evaluation, warning domain events, and bootstrap controls.
- `account-service` owns accounts, account currency mirrors, beneficiaries, and customer notification rows/read state.
- `frontend` exposes INR account/movement/schedule inputs, causal schedule evidence, refresh results, and warning acknowledgement.
- Existing JWT ownership, `ROLE_ADMIN`/`ROLE_INTERNAL_SERVICE`, MFA/step-up, idempotency, ledger immutability, and maintenance controls remain authoritative.

## 4. INR end-to-end path

INR becomes a supported two-decimal ISO-4217 currency beside USD, EUR, and GBP.

- Account creation accepts and returns INR; existing accounts retain immutable currency.
- Beneficiary, deposit, withdrawal, transfer, and scheduled-transfer validation accepts INR.
- Ledger bootstrap system currencies include INR so `CLEARING`, `SUSPENSE`, and `FEE` accounts can be seeded before INR posting.
- Outcome Protection accepts only same-currency customer ledger accounts and persists INR inputs/snapshots/results using `BigDecimal` and `NUMERIC(19,2)`.
- The frontend uses the Indian grouping/rupee format for INR while API values remain decimal amounts.
- No FX, currency aggregation, currency conversion, or cross-currency journal is introduced.

## 5. Authoritative active-schedule forecast

Forecast input is derived only from persisted `scheduled_transfers` rows owned by the authenticated scenario owner and currently in `ACTIVE` status.

- Currency must exactly match the scenario currency.
- An occurrence is outgoing when its source is selected, incoming when its destination is selected, and zero/netted when both selected accounts are inside the scenario.
- `next_run_at` and `end_at` remain authoritative UTC instants. Local horizon inclusion is evaluated in the scenario's explicit IANA time zone.
- Recurrence uses the same cadence function as the execution worker (`WEEKLY`, `BIWEEKLY`, `MONTHLY`) so preview and execution do not drift.
- Horizon start and end dates are inclusive. Occurrences after `end_at` or outside the horizon are excluded.
- Snapshot/timeline events include schedule ID, persisted occurrence instant, local evaluation date, signed amount, currency, status, cadence, accounts, and evaluation time zone.
- Pausing/canceling a schedule removes it from a fresh forecast; resuming restores it. Refresh never invokes schedule mutation or execution code.

## 6. Unsafe divergence evidence and notification

A refresh compares the saved immutable source fingerprint/result with a new ledger-and-schedule snapshot and reruns the saved assumptions.

- Every distinct evaluation state persists one immutable `DIVERGENCE_EVALUATED` event containing saved/current fingerprints, saved/fresh safety, relevant balance thresholds, and fresh ledger/schedule evidence.
- A warning is eligible only when the saved result was safe and the fresh result is at risk.
- Warning events are deduped by customer, scenario version, and fresh source fingerprint.
- Account-service notification creation uses the warning event as a stable dedupe source. A repeated refresh may retry a prior best-effort delivery failure; account-service dedupe prevents duplicate inbox rows.
- Refresh returns evaluation event ID, warning event ID, acknowledgement state, delivery result, and fresh proof.
- Warning acknowledgement is customer-owned, idempotent, and persists an immutable `WARNING_ACKNOWLEDGED` event. It marks domain acknowledgement only; the existing notification read route remains the notification-inbox state boundary.

## 7. Fresh-database bootstrap hardening

Add an admin-only, read-only bootstrap preflight and an opt-in fresh-database startup bootstrap mode.

- Preflight reports maintenance confirmation, blockers, required/missing system accounts, relevant row counts, and whether bootstrap can run. It never writes data.
- Bootstrap still requires both `enabled=true` and `maintenanceMode=true` and retains all legacy-hold and processing-transaction blockers.
- Required system currencies are explicit configuration with a safe default of `USD,EUR,GBP,INR`.
- Opt-in startup automation is disabled by default, requires maintenance mode, requires ledger authority to be disabled, and is restricted to a fresh financial database. Any mismatch fails startup closed.
- Each bootstrap attempt gets durable audit evidence with actor, mode, business date, outcome, counts/currencies, and sanitized failure reason.
- Repeated bootstrap reuses system/customer accounts and opening-journal idempotency; it never silently creates or moves customer funds.
- Operators must still run reconciliation and require zero unexplained critical exceptions before enabling ledger authority.

## 8. API additions/changes

- `GET /api/admin/ledger/bootstrap/preflight?maintenanceMode=true` returns the read-only preflight.
- Existing `POST /api/admin/ledger/bootstrap` returns a durable `runId` in addition to counts.
- Existing Outcome Protection refresh adds evaluation/warning/acknowledgement evidence fields.
- Existing warning acknowledgement returns the acknowledgement event rather than an empty body.
- Existing request routes remain source compatible; added response fields are additive.

## 9. Acceptance criteria

- A fresh environment can explicitly preflight and seed INR system accounts without weakening maintenance or authorization gates; a non-maintenance attempt fails closed and is auditable.
- A customer can create/fund an INR account, create an INR active schedule, and run an INR Outcome Protection scenario whose persisted API proof and UI use exact two-decimal values and rupee formatting.
- Active, owned, same-currency occurrences appear as `SCHEDULED_TRANSFER` causal events at the correct local date; paused/canceled, other-owner, other-currency, net-zero, end-bounded, and out-of-horizon occurrences do not.
- Pausing/resuming a schedule changes a fresh scenario forecast without executing a transfer.
- A saved-safe scenario can become at risk after an authoritative ledger or schedule change, producing immutable evaluation/warning evidence and one deduped account-service notification.
- Notification delivery can be retried after failure without duplicating the customer inbox row.
- The customer can acknowledge the returned warning event idempotently.
- Focused tests plus full account-service, transaction-service, frontend test/build, fresh PostgreSQL migration/bootstrap, cross-service smoke, and browser walkthrough pass.

## 10. Explicit non-goals

- No automatic transfer creation, execution, pause, resume, cancellation, or amount/date editing.
- No executable reserve, hold, sweep, or low-balance guardrail.
- No bypass or redesign of MFA, step-up authorization, ownership checks, or idempotency.
- No external bill/income feeds, Open Banking aggregation, FX, multi-currency scenario aggregation, investment advice, Monte Carlo claims, email, SMS, or push delivery.
- No automatic ledger-authority cutover and no rollback to mutable balance authority after authoritative journals post.

## 11. Recommended next phase

After this increment, the next separately reviewed phase should add operator-facing divergence/notification observability and delivery retry metrics, then consider consent-bound executable protections only through existing authorized scheduled-transfer and MFA flows. Regulatory review should precede any automated prioritization, overdraft-like behavior, external-data ingestion, or customer-specific financial recommendations.
