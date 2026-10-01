# Balance Shield consent-driven executable guardrails

**Status:** Phase 2 implementation contract
**Date:** 2026-07-16
**Authoritative baseline:** `origin/main` at `0bb9f3aae5d1488327453a9a9bbef585fb53511f` (Phase 1 PR #39)

## Outcome and safety boundary

Phase 2 turns only the deterministic `RESERVE_BUFFER` draft into an explicitly configured, customer-invoked same-currency top-up policy. Forecast state is never authorization. The customer must select an owned funding account and protected account, accept versioned terms, confirm bounded limits and expiry, complete MFA activation, and explicitly confirm each execution request. Every execution enters the existing `TransferAuthorizationService`, so ownership, account status, beneficiary/risk policy, spending limits, balance checks, idempotency, ledger posting, and compensation remain authoritative.

No background job initiates guardrail transfers. The runtime kill switch defaults disabled. A response is never shown as successful until the existing transfer path reports a completed transaction.

## Threat model

| Threat | Required control |
| --- | --- |
| Stolen session activates a policy | Action-bound account-service MFA challenge and single-use proof; activation fingerprint covers guardrail, accounts, terms, limits, and expiry. |
| Cross-customer policy or account access | JWT owner is authoritative; draft, policy, source account, destination account, execution, and audit reads are owner-scoped. Unknown and foreign identifiers return the same denial. |
| Replay or concurrent execution moves funds twice | Customer-scoped idempotency key plus request fingerprint, database uniqueness, pessimistic policy/execution locks, reserved pending amount, and the existing transfer idempotency key. |
| Revocation races with execution or authorization | Revocation, execution, and authorization lock the policy first. Revocation cancels pending transfer authorizations and releases reservations before returning. Completed transfers remain immutable evidence. |
| Limit bypass through parallel pending challenges | Pending actions reserve policy capacity; per-action, total, count, expiry, same-currency, and current-balance trigger checks run while the policy lock is held. |
| Operator or configuration accidentally enables movement | Persisted global execution control defaults off; admin role is required to change it; each change creates immutable operator evidence. Missing control/configuration fails closed. |
| Terms change after consent | Terms version and SHA-256 hash are persisted with evidence. A mismatch makes the policy effectively suspended and requires new consent rather than silently inheriting new terms. |
| Account ownership/status/balance changes after activation | Ownership and currency are rechecked before every attempt; the existing transfer path rechecks debit status, spending limits, available balance, and ledger state at execution time. |
| Downstream failure after debit | Existing debit-hold compensation or authoritative double-entry journal transaction remains the only money-movement implementation. Guardrail evidence records failure/manual-action state without claiming success. |
| Notification outage | Lifecycle events and notification delivery are committed transactionally through the Phase 1 outbox, with retry, terminal, SLA, and dedupe evidence. Notification failure never changes policy or transfer state. |
| Misleading UI | Draft, consent pending, active, globally suspended, customer suspended, revoked, expired, awaiting transfer authorization, failed, and completed are distinct states. |

## Failure model

- MFA enrollment missing, challenge expiry, invalid proof, proof replay, and terms mismatch fail without activation.
- Kill switch off, policy suspension/revocation/expiry, source drift, threshold already satisfied, exhausted limits, insufficient balance, frozen account, or risk rejection fail before or inside the authorized transfer boundary.
- A risk challenge may leave an execution `AWAITING_AUTHORIZATION`; reserved capacity prevents another request from consuming the same allowance.
- Retrying an authorization uses the same execution and transfer idempotency keys. Proof-consumed transfer failures remain retryable through the existing authorization recovery behavior.
- Revocation cancels pending authorizations; a transaction already completed before the revocation lock is retained in the immutable audit trail and is never reversed implicitly.
- Compensation and manual-action-required outcomes are surfaced exactly as returned by the existing transfer implementation. The guardrail layer never fabricates a compensating transfer.

## State model

`DRAFT -> CONSENT_PENDING -> ACTIVE -> SUSPENDED -> ACTIVE` is allowed only through explicit customer actions and current terms. `ACTIVE|SUSPENDED|CONSENT_PENDING -> REVOKED` is terminal. Expiry is terminal. The global kill switch creates an effective `SUSPENDED` state without rewriting customer consent.

Executions are `REQUESTED -> AWAITING_AUTHORIZATION -> COMPLETED`, with terminal `CANCELLED` or `FAILED` states. Database locks and immutable lifecycle events define ordering during races.

## Non-goals

- No autonomous/background transfer, sweep, hold, schedule mutation, overdraft, credit, investment, securities, or executable FX behavior.
- No transfer between currencies and no use of forecast FX quotes for execution.
- No bypass or replacement of MFA, transfer risk evaluation, spending limits, account ownership/status, ledger posting, scheduled-transfer controls, or compensation.
- No guarantee that a top-up preserves the modeled outcome; forecasts remain informational and must be refreshed separately.
- No external payment rail, Open Banking initiation, regulatory suitability decision, or personalized financial advice.
- No implicit reversal after revocation; completed ledger history remains immutable.

## Regulatory and external boundaries

Production enablement requires approved consent language/version governance, withdrawal and complaint handling, retention/accessibility rules, transaction-notification provider operations, MFA/IdP controls, fraud/risk ownership, transfer-limit policy, and jurisdiction-specific payment authorization review. The repository provides evidence and enforcement hooks; it does not supply those approvals.
