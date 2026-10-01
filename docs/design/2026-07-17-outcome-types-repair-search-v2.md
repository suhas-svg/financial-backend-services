# Outcome Types and Repair Search V2

**Status:** Implementation contract
**Date:** 2026-07-17
**Branch:** `codex/outcome-types-repair-search-v2`
**Authoritative baseline:** `origin/main` at `2ede6577c08c66fd00787e4cc55894bccbbd1344` (PR #42)

## 1. Objective

Extend Outcome Protection from a minimum-available-balance proof with one repair draft into a deterministic, replay-proven comparison of protected outcomes and bounded repair alternatives.

A customer may continue to protect a balance floor, or protect one owned, active scheduled obligation that falls inside the forecast horizon while optionally retaining a non-negative balance floor. The result explains balance-floor breaches and whether the selected obligation remains executable. Repair candidates are eligible only when replay against the same immutable source snapshot restores every selected invariant throughout the horizon.

Customer-facing language remains **Outcome Protection** and **Balance Shield**. “Money Debugger” remains an internal product concept.

## 2. Ownership and safety boundaries

`transaction-service` continues to own immutable scenario versions, authoritative ledger and scheduled-transfer snapshots, deterministic simulation, bounded failure search, bounded repair search, replay certificates, guardrail drafts, divergence evidence, and warning outbox rows. `account-service` continues to own customer accounts, spending controls, MFA, and notification delivery state. The frontend extends the existing `/outcome-protection` route.

This increment does not add a third service or an autonomous executor. Simulation and draft selection never move funds, reserve funds, mutate a schedule, alter a spending limit, post a journal, contact an external payment rail, or treat forecast state as authorization.

The existing same-currency `RESERVE_BUFFER` Balance Shield top-up policy remains the only executable repair type. Its existing versioned consent, action-bound MFA, explicit per-action confirmation, risk, ownership, spending-limit, idempotency, ledger, lifecycle, notification, and global kill-switch controls remain authoritative.

## 3. Protected outcome model

`BALANCE_FLOOR` preserves the existing behavior. The selected accounts must remain at or above `protectedMinimum` for every day in the horizon.

`SCHEDULED_OBLIGATION` requires a `protectedScheduleId` and the schedule’s current optimistic version. Creation fails closed unless the schedule:

- is owned by the authenticated customer;
- is `ACTIVE`;
- has at least one due occurrence inside the inclusive horizon;
- uses a supported currency and has valid forecast-only conversion evidence when its currency differs from the forecast base;
- debits one of the selected authoritative customer ledger accounts;
- has a submitted version equal to the persisted version; and
- is not already processing or otherwise outside the existing schedule state model.

The immutable obligation snapshot records schedule ID and version, state, owner, source/destination IDs and customer-ownership flags, amount/currency, schedule type/cadence, next due instant, due local date/time, source time zone, horizon evaluation time zone, end instant, and capture time. The source snapshot also retains every selected ledger projection version and every forecast schedule version.

For an obligation occurrence to be successful in the proof, its source must have enough modeled available balance immediately before the debit and the resulting timeline must satisfy the optional balance floor. A causal proof distinguishes `BALANCE_FLOOR_BREACH`, `PROTECTED_OBLIGATION_INSUFFICIENT_FUNDS`, and `PROTECTED_OBLIGATION_MISSING_OR_CHANGED`.

## 4. Repair candidate model

The deterministic compiler may produce:

- `RESERVE_BUFFER`: exact same-currency top-up required before the first unresolved breach;
- `SHIFT_OPTIONAL_SCHEDULE`: defer an eligible non-critical scheduled occurrence to the first stable date inside the horizon;
- `REDUCE_OPTIONAL_SCHEDULE`: reduce an eligible non-critical scheduled occurrence by the smallest modeled amount that can restore the invariants;
- `TEMPORARY_SPENDING_LIMIT`: advisory-only bounded cap on discretionary outflow when existing spending-control semantics can represent the amount; and
- `REVIEW_FLEXIBLE_EXPENSES`: advisory fallback over flexible, non-critical assumptions.

The protected obligation is never a repair target. Schedule candidates exclude other owners, non-active state, processing occurrences, critical or immutable schedules, schedules outside the horizon, schedules whose source is not in the selected scope, cross-currency actions requiring executable FX, and candidates whose replay does not restore all invariants. Because the current scheduled-transfer schema has no criticality field, V2 uses an explicit conservative eligibility marker: only a non-protected schedule whose customer reference is exactly `FLEXIBLE` or `OPTIONAL` may generate shift/reduction previews; every other schedule is rejected with visible evidence. This increment models schedule shift/reduction and temporary-limit actions only as consent drafts; it does not call schedule or spending-limit mutation APIs.

Candidate IDs and canonical payloads are stable. Every rejected action records a machine-readable reason and customer-safe explanation.

## 5. Bounded deterministic repair search

Candidate actions are canonically ordered by action type, target ID, effective date, amount, and stable action ID. The engine enumerates combinations from size one up to `OUTCOME_PROTECTION_REPAIR_MAX_COMBINATION_SIZE`, stopping at `OUTCOME_PROTECTION_REPAIR_MAX_EVALUATED_COMBINATIONS`.

Each candidate combination is applied to a copy of the same immutable cashflow/source snapshot. Replay never reads live state and never invokes mutation code. A combination is eligible only when every selected protected invariant is restored throughout the full horizon.

Eligible alternatives are ranked lexicographically by:

1. restore all protected invariants;
2. fewer actions;
3. lower disruption and criticality score;
4. less money moved, reduced, or deferred; and
5. stable canonical action IDs.

The API returns ranked alternatives, per-action explanations, replay output, ranking factors, rejection reasons, evaluated combination count, configured caps, and whether the search was capped. It claims optimality only within the evaluated bounded search.

## 6. Immutable persistence and replay certificate

A forward-only migration adds:

- protected-outcome type and immutable protected-obligation/canonical-input snapshots to scenario versions;
- engine version, canonical inputs, candidate actions, replay output, certificate hash, ranking factors, rejection reasons, and repair-search cap evidence to simulation results; and
- selected repair alternative and selection idempotency evidence to guardrail drafts.

The certificate is SHA-256 over the engine version, canonical scenario input, authoritative source versions, candidate actions, replay outputs, ranking factors, rejection reasons, and configured caps. Existing append-only triggers continue to reject updates/deletes of versions, results, and domain events. Draft selection is owner-scoped, confirmation-gated, and idempotent; it changes draft-selection state only.

## 7. API and UI

Existing `/api/outcome-protection` routes are extended additively.

- Scenario requests add outcome type, protected schedule ID, and protected schedule version.
- Scenario responses add the immutable protected-obligation snapshot, invariant proof, repair alternatives, replay certificates, ranking factors, rejection reasons, and repair search caps.
- `POST /api/outcome-protection/repairs/{guardrailId}/select` records explicit preview selection with `Idempotency-Key`; it does not consent to or execute an action.

The customer UI lets the customer choose an outcome type, select an eligible active schedule, keep or remove the floor by setting it to zero, inspect the breaking causal chain, compare ranked repair alternatives, expand replay proof and certificate details, understand unavailable actions, and select a preview draft before any existing consent flow.

Customer and operator wording must distinguish simulation, preview selection, consent, activation, and execution.

## 8. Divergence and notification behavior

Refresh and the scheduled monitor compare both ledger projection versions and the protected obligation’s current version/state against the immutable snapshot. A changed, inactive, missing, or newly unsafe protected obligation produces append-only `DIVERGENCE_EVALUATED` evidence and, when the saved proof was safe, one deduplicated `OUTCOME_PROTECTION_AT_RISK` warning/outbox row per fresh source fingerprint.

Monitoring never changes the obligation, a spending limit, a ledger entry, or funds. Notification delivery remains fail closed behind the existing durable outbox and provider-activation boundaries.

## 9. Threat and failure model

- Foreign schedule IDs and account IDs receive the same owner-scoped denial as missing records.
- Stale schedule versions fail before scenario persistence.
- Missing/stale FX evidence fails closed and is never reused for execution.
- Search cap exhaustion is reported explicitly and does not imply global optimality.
- Concurrent selection replays the owner-scoped idempotency result or rejects a conflicting fingerprint.
- Simulation and selection share no code path that writes scheduled transfers, spending limits, transactions, ledger journals, holds, or transfer authorizations.
- A later schedule-state or projection-version change invalidates the saved operational assumption and is surfaced as divergence evidence.
- Provider/notification unavailability preserves evidence and schedules bounded retry; it never changes financial state.

## 10. Validation contract

Focused engine tests cover one- and multi-action repairs, stable ranking, configured caps, stale source rejection, obligation failure, schedule eligibility, replay certificate stability, rejection reasons, and no mutation during simulation. Service/controller tests cover ownership, idempotency, immutable persistence, selection, divergence, and notification behavior.

Release validation requires both full Maven suites with exact pass/skip counts, frontend tests/build, a fresh PostgreSQL Flyway run, bounded Docker cross-service smoke, and a browser walkthrough that protects rent/payment plus a floor, compares at least three eligible repair types, expands replay proof, selects a draft, and verifies no schedule, limit, ledger, or fund mutation from simulation/selection.

## 11. Explicit non-goals and remaining beta work

This PR does not include autonomous/background money movement, automatic schedule or limit changes, overdraft or credit, executable FX, external payment rails, bypasses of existing controls, a public synthetic sandbox, licensed read-only aggregation, white-label or multi-tenant packaging, privacy-safe product analytics, or a real pilot.

Public synthetic sandboxing, licensed aggregation, tenant packaging, analytics governance, external provider certification, approved product/consent language, and a controlled real pilot remain separate beta-enablement work.
