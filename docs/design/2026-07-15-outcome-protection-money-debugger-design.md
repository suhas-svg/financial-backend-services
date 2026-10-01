# Outcome Protection (Money Debugger) MVP Design

**Status:** Approved for implementation
**Date:** 2026-07-15
**Branch:** `codex/money-debugger-mvp`
**Authoritative baseline:** `origin/main` at `50172cc53a7d8ec77b6e48846a964c775f218385`

## 1. Product objective

Build a deterministic Personal Reverse-Stress Lab and Guardrail Compiler. A customer protects a minimum available balance for selected accounts over a bounded horizon. The service snapshots authoritative ledger availability, known active scheduled transfers, and explicit customer assumptions; forecasts the balance; searches bounded combinations of plausible shocks for the smallest failure set; and compiles read-only guardrail drafts that restore or warn about the protected outcome.

Customer-facing language is **Outcome Protection** and **Balance Shield**. “Money Debugger” remains an internal product concept.

## 2. Ownership and boundaries

`transaction-service` owns scenario definitions and versions, ledger/schedule snapshots, deterministic simulations, shock search, repair compilation, divergence evaluation, domain events, and guardrail drafts. `account-service` continues to own customer notifications. The frontend exposes the workflow as a primary customer route.

The MVP is advisory and preview-only. It does not hold or transmit funds, execute or change scheduled transfers, contact external rails, make investment or securities recommendations, silently move money, or treat a guardrail acceptance as authorization for money movement.

## 3. Inputs and immutable snapshots

A scenario version contains:

- authenticated owner and selected customer ledger account IDs;
- one ISO-4217 currency and explicit IANA time zone;
- local horizon start date and 1-90 day horizon;
- protected minimum available balance;
- explicit assumptions, each with stable ID, date, signed decimal amount, type, label, flexibility, and criticality;
- shock candidates, each with stable ID, type, target assumption, bounded parameters, and customer-facing label;
- an authoritative ledger snapshot with available balances and projection versions;
- an active scheduled-transfer snapshot expanded only inside the horizon;
- a canonical source fingerprint and creation timestamp.

Versions and simulation results are append-only. PostgreSQL triggers reject updates and deletes. A new customer edit creates a new version; it never changes historical proof.

## 4. Deterministic forecast and reverse stress

All money uses `BigDecimal` at currency scale. Dates are evaluated in the scenario time zone and output is ordered by date, source priority, then stable event ID.

The baseline starts with the sum of authoritative available balances. It applies:

1. scheduled transfers whose source or destination is selected (outgoing negative, incoming positive, selected-to-selected net zero);
2. explicit customer assumptions as signed events.

The engine records every daily event, closing balance, lowest balance, and first protected-outcome breach.

Reverse stress enumerates shock combinations by ascending cardinality and lexicographic shock ID. It stops at the first cardinality that causes a breach and selects the combination with the smallest total normalized severity, then earliest failure date, then lexicographic ID. Caps are configurable and enforced for horizon days, shock candidates, combination size, and evaluated combinations. A capped run reports that it is bounded rather than claiming global optimality beyond the configured search space.

Supported shocks:

- `INCOME_DELAY`: move one positive assumption later by a bounded day count;
- `INCOME_REDUCTION`: reduce one positive assumption by a bounded percentage;
- `EXPENSE_SPIKE`: increase the magnitude of one negative assumption by a bounded amount;
- `PAYMENT_TIMING_SHIFT`: move one negative assumption earlier by a bounded day count.

The result includes exact failure date, lowest balance, triggering events, applied shocks, assumptions, timeline, evaluated-combination count, and minimality explanation. It contains no generated or opaque AI prose.

## 5. Repair and guardrail compilation

For a failing result the compiler derives deterministic, preview-only candidates:

- a low-balance warning threshold equal to the protected minimum;
- a temporary reserve-buffer draft for the exact maximum shortfall;
- where a flexible non-critical expense exists, a draft to review/defer the smallest set of such expenses whose combined value covers the shortfall.

Candidates are ordered by number of actions, total customer impact, then stable ID. The accepted candidate is the smallest repair set found inside the same bounded proof model.

Every guardrail draft records threshold, currency, selected-account scope, scenario/result/version, preview text, expiry, and status. Acceptance requires a customer confirmation flag and an `Idempotency-Key`, records an immutable analytics/audit event, and changes only the draft consent status. It does not activate execution.

## 6. Divergence monitoring and notifications

Saved active scenarios are periodically compared with current ledger projection versions/available balances and active schedules. The evaluator reruns the saved immutable assumptions against a fresh source snapshot. When the protected outcome changes from safe to at risk, transaction-service emits a deduped best-effort `OUTCOME_PROTECTION_AT_RISK` notification through account-service and records a warning event. Notification failure does not change the scenario or simulation result. Customers can also request a refresh explicitly.

## 7. APIs

- `POST /api/outcome-protection/scenarios` creates version 1, runs it, and returns proof plus drafts. Requires `Idempotency-Key`.
- `GET /api/outcome-protection/scenarios` lists the authenticated customer’s scenarios.
- `GET /api/outcome-protection/scenarios/{scenarioId}` returns the latest owned result and drafts.
- `POST /api/outcome-protection/scenarios/{scenarioId}/versions` creates a new immutable version and simulation. Requires `Idempotency-Key`.
- `POST /api/outcome-protection/scenarios/{scenarioId}/refresh` evaluates divergence without changing the saved version.
- `POST /api/outcome-protection/guardrails/{guardrailId}/accept` requires `{ "confirmed": true }` and `Idempotency-Key`.
- `POST /api/outcome-protection/warnings/{eventId}/acknowledge` records an idempotent customer acknowledgement.

All endpoints resolve the owner from the JWT. Cross-customer access is rejected. Mutation idempotency keys are scoped by customer and operation and reject conflicting payload fingerprints.

## 8. Analytics-ready events

The transaction database stores append-only events with scenario, version, result/guardrail IDs, customer, timestamp, and structured fields for:

- `SCENARIO_COMPLETED`;
- `FIRST_PROTECTED_OUTCOME`;
- `GUARDRAIL_DRAFT_ACCEPTED`;
- `WARNING_ACKNOWLEDGED`;
- `VERIFIED_PREVENTED_SHORTFALL` (schema-ready; emitted only when a later fresh evaluation verifies an accepted draft’s modeled protection).

No external analytics dependency is added.

## 9. Acceptance criteria

- Creating a scenario snapshots only customer-owned, same-currency authoritative ledger accounts.
- Active scheduled transfers and explicit assumptions appear as separate causal timeline events.
- Identical inputs and source snapshots produce identical proof, failure set, and repairs.
- Bounded search finds a one-shock failure before any two-shock failure and explains the configured minimality boundary.
- Results contain exact failure date, lowest balance, triggers, assumptions, and a daily timeline.
- Guardrail drafts are preview-only, scoped, expiring, confirmation-gated, idempotent, and audited.
- Cross-customer reads/mutations fail; conflicting idempotency reuse fails.
- Source divergence can produce one deduped customer warning without blocking other workflows.
- Focused engine/API tests, transaction-service tests, account-service tests, frontend tests/build, and a bounded cross-service smoke check pass.

## 10. Explicit non-goals and next phase

V1 excludes automatic fund movement, scheduled-transfer mutation, MFA-backed activation, external bill/income ingestion, bank aggregation, email/SMS/push, multi-currency aggregation or FX, stochastic Monte Carlo claims, unbounded optimization, regulated financial planning, white-label distribution, and securities advice. A later activation phase must separately preserve scheduled-transfer authorization, MFA, idempotency, cancellation, and audit controls before any executable guardrail is introduced.
