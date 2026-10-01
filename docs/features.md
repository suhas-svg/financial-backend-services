# Features

What the customer app, the operations console and the two services do.

## Main Features

### Customer App

- Register, login, and logout with JWT-backed sessions.
- Store the JWT in memory only; a browser reload intentionally requires login.
- Decode JWT roles client-side for route guards and navigation.
- View dashboard totals, account cards, recent transactions, limits, and personal stats.
- Create, edit, close, and filter accounts while preserving financial history.
- Create `CHECKING`, `SAVINGS`, and `CREDIT` accounts with type-specific validation.
- See available balance as the primary spendable amount and ledger balance as secondary detail.
- See frozen accounts with hold warnings and status reasons.
- Deposit, withdraw, and transfer money.
- Show explicit processing and confirmed-success states for deposits, withdrawals, and transfers, and reset each form only after the backend confirms completion.
- Save external recipient accounts with immediate guidance when a customer enters one of their own accounts; own-account movement stays in the standard transfer flow.
- Enroll a TOTP authenticator, generate single-use recovery codes, and manage MFA from the Security page.
- Configure per-account daily transfer and withdrawal limits from Security; reductions are immediate and MFA-verified increases cool for 24 hours.
- Complete risk-based step-up verification before high-risk transfers are posted.
- Create one-time or recurring scheduled transfers between accounts.
- Pause, resume, cancel, and inspect scheduled transfer run history.
- Protect a minimum available balance with the Outcome Protection / Balance Shield reverse-stress lab.
- Review exact baseline and stressed timelines, minimal failure sets, preview guardrails, and explicitly confirmed consent-driven top-ups.
- Use available-balance validation for withdrawals and outgoing transfer sources.
- Keep frozen accounts selectable for credits, while debit-source controls are disabled.
- Generate an `Idempotency-Key` per money-movement submit.
- Search and filter transaction history.
- View transaction details and user transaction stats.
- Dispute eligible completed transactions from the last 60 days.
- Review submitted disputes and resolution notes in the customer dispute history.
- Review in-app notifications, unread counts, and message state for account, transaction, and dispute events.

### Admin/Ops App

- Hide admin navigation for normal users.
- Guard admin routes using `ROLE_ADMIN` from JWT claims.
- Search accounts across users with owner and account type filters.
- Filter accounts by lifecycle status and freeze or unfreeze accounts with required reasons.
- Review both available and ledger balances in account oversight.
- Use existing account create/update/delete flows for admin oversight.
- Monitor account service health, metrics, deployment information, and manual health checks.
- Monitor transaction service health, transaction/system stats, alert status, and available metrics.
- Search operational transaction views.
- Reverse transactions using the backend reversal endpoint.
- View reversal-related status panels.
- Review the transaction-service audit log with admin-only summary counters, filters, event search, and selected-event details.
- Review transaction risk alerts with summary counters, filters, detail inspection, and review/dismiss/escalate actions.
- Review customer transaction disputes with summary counters, filters, claim/status actions, and internal notes.
- Reconstruct investigation context across transactions, audit events, risk alerts, risk cases, and case notes.
- Print investigation reports with current filters, key findings, and a timeline preview.
- Export investigation timelines as admin-only CSV files using the same investigation filters used by the timeline view.
- Run daily ledger reconciliation against each projection's persisted opening balance plus complete immutable posting history, including journals later compensated by reversals.
- Inspect reconciliation run ownership, timestamps, per-check results, run-to-exception links, and expected/actual balance evidence.
- Treat approved, denied, and closed disputes as terminal in both the API and operations console.

### Backend Services

- Account service:
  - Authentication and JWT issuance.
  - User registration.
  - Account CRUD.
  - Account lifecycle status with `ACTIVE` and `FROZEN` states.
  - Admin-only status updates with reason and updated-by metadata.
  - Ledger balance, available balance, and account debit hold ownership.
  - Debit hold placement, capture, and release with idempotent hold IDs.
  - Frozen-account debit rejection while preserving deposits and incoming credits.
  - Positive balance operations update both ledger and available balances.
  - Account type validation for checking, savings, and credit accounts.
  - Health, metrics, and deployment endpoints.
  - Customer notification APIs for listing, summary counts, marking one read, and marking all read.
  - Internal/admin notification creation API secured to `ROLE_ADMIN` and `ROLE_INTERNAL_SERVICE`.
  - Encrypted TOTP enrollment, verification, recovery-code rotation, and MFA disablement.
  - Short-lived, action-bound step-up challenges and one-time authorization proofs.
- Transaction service:
  - Deposit, withdrawal, transfer, and scheduled transfer endpoints.
  - Scheduled transfer persistence, authenticated APIs, and worker execution for one-time and recurring transfers.
  - Immutable, versioned Outcome Protection scenarios and deterministic reverse-stress simulation over authoritative ledger balances, active schedules, and explicit assumptions.
  - Bounded minimal-failure search, causal proof, read-only guardrail compilation, analytics-ready domain events, and divergence monitoring.
  - Frozen-account debit enforcement before withdrawals and outgoing transfer debits.
  - Pending debit authorization flow for withdrawals and outgoing transfers.
  - Debit hold placement and capture before completing debit transactions.
  - Debit hold release or compensation paths for failed debit orchestration.
  - Transaction history and search.
  - Transaction stats and monitoring endpoints.
  - Idempotency and reversal workflows.
  - Persistent audit log storage for high-value transaction and security events.
  - Admin-only audit log search, detail, and summary APIs.
  - Persistent risk alert review queue for conservative transaction risk rules.
  - Admin-only risk alert search, detail, summary, and status update APIs.
  - Customer dispute submission and admin-only dispute queue APIs.
  - Admin-only investigation timeline, summary, and CSV export APIs.
  - Account-service integration for balance updates.
  - Configurable risk-based transfer authorization for high-value, new-beneficiary, rapid-transfer, and recent-unfreeze signals.
  - Durable pending authorization records that prevent transaction and ledger posting before successful verification.
  - Best-effort account-service notification emission for completed/failed transfer outcomes, scheduled transfer lifecycle events, and dispute lifecycle updates.

## Notification Center

The v1 notification center is in-app only. It does not send email, SMS, push notifications, replies, or attachments.

Customer API:

- `GET /api/notifications?page=&size=&status=&type=&sourceType=&from=&to=`
- `GET /api/notifications/summary`
- `PATCH /api/notifications/{notificationId}/read`
- `PATCH /api/notifications/read-all`

Internal creation API:

- `POST /api/internal/notifications`

Notification records are customer-owned in `account-service`. Customer endpoints always use the authenticated user, while internal/admin callers may create notifications for any `userId`. The internal endpoint requires `ROLE_ADMIN` or `ROLE_INTERNAL_SERVICE`.

Event sources currently create notifications for account freeze/unfreeze, transfer completion/failure, scheduled transfer creation/pause/resume/cancel/execution/failure, dispute creation, and dispute status changes to `APPROVED`, `DENIED`, or `CLOSED`. Source workflows treat notification delivery as best-effort: failures are logged and do not roll back money movement, scheduled transfer processing, account status changes, or dispute updates. Dedupe keys keep repeated source events from creating duplicate inbox rows.

## Outcome Protection / Balance Shield

The customer route `/outcome-protection` is a deterministic Personal Reverse-Stress Lab. A customer selects owned ledger accounts, a forecast base currency, a protected minimum, a 1-90 day horizon, dated assumptions, and bounded shocks. Outcome Types and Repair Search V2 also lets the customer protect one owned, active, version-bound scheduled obligation inside the horizon, optionally together with a balance floor. Transaction-service snapshots authoritative available balances, ledger projection versions, schedule identity/state/version/ownership/due semantics, and active scheduled transfers, then returns the baseline timeline, causal invariant breaches, smallest bounded failure set, and ranked replay-proven repair alternatives.

Customer API:

- `POST /api/outcome-protection/scenarios` (requires `Idempotency-Key`)
- `GET /api/outcome-protection/scenarios`
- `GET /api/outcome-protection/scenarios/{scenarioId}`
- `POST /api/outcome-protection/scenarios/{scenarioId}/versions` (requires `Idempotency-Key`)
- `POST /api/outcome-protection/scenarios/{scenarioId}/refresh`
- `POST /api/outcome-protection/guardrails/{guardrailId}/accept` (records preview acceptance only)
- `POST /api/outcome-protection/repairs/{guardrailId}/select` (records owner-scoped preview selection only; no mutation or consent)
- `GET /api/outcome-protection/guardrails/terms`
- `POST /api/outcome-protection/guardrails/{guardrailId}/consent` (versioned informed consent and `Idempotency-Key`)
- `POST /api/outcome-protection/guardrails/{guardrailId}/activate` (action-bound MFA; activation moves no money)
- `POST /api/outcome-protection/guardrails/{guardrailId}/execute` (explicit confirmation and `Idempotency-Key`)
- `POST /api/outcome-protection/guardrail-executions/{executionId}/authorize` (risk-based MFA when required)
- `POST /api/outcome-protection/guardrails/{guardrailId}/suspend`, `/resume`, or `/revoke`
- `POST /api/outcome-protection/warnings/{eventId}/acknowledge`

Scenario inputs, ledger/schedule snapshots, and simulation results are versioned and immutable. USD, EUR, GBP, and INR are supported; INR remains decimal-safe end to end and is rendered with Indian digit grouping in the frontend. Active customer-owned schedules are expanded with status, cadence, effective time zone, currency, and inclusive horizon boundaries in the causal timeline. Search is capped by `OUTCOME_PROTECTION_MAX_COMBINATION_SIZE` (default `3`) and `OUTCOME_PROTECTION_MAX_EVALUATED_COMBINATIONS` (default `5000`); capped results say so explicitly.

Repair Search V2 generates deterministic `RESERVE_BUFFER`, `SHIFT_OPTIONAL_SCHEDULE`, `REDUCE_OPTIONAL_SCHEDULE`, `TEMPORARY_SPENDING_LIMIT`, and `REVIEW_FLEXIBLE_EXPENSES` candidates where eligible. It never targets the protected obligation. Every candidate combination is replayed against the same immutable snapshot and ranked by restored invariants, action count, disruption, modeled money moved/deferred, and stable IDs. Results persist engine/canonical input/source-version/candidate/replay/ranking/rejection evidence plus a SHA-256 certificate. Repair search is separately capped by `OUTCOME_PROTECTION_REPAIR_MAX_COMBINATION_SIZE` (default `3`) and `OUTCOME_PROTECTION_REPAIR_MAX_EVALUATED_COMBINATIONS` (default `500`); the UI explicitly disclaims optimality outside those bounds. Schedule/limit alternatives remain preview-only. Only the pre-existing same-currency reserve-buffer path can proceed through versioned consent, MFA, explicit confirmation, risk, spending-limit, ledger, lifecycle, notification, and global kill-switch controls.

Refresh and the five-minute monitor compare the saved proof with fresh authoritative ledger and scheduled-transfer state. Each comparison persists a `DIVERGENCE_EVALUATED` evidence event. A saved-safe result that is now unsafe transactionally enqueues one deterministic `OUTCOME_PROTECTION_AT_RISK` delivery. Bounded retry/backoff, terminal failure, SLA escalation, and account-service receipt evidence are visible without changing balances or schedules; acknowledgement remains owned, audited, and idempotent.

Reserve-buffer drafts may become bounded same-currency top-up policies only after versioned informed consent and action-bound MFA. Activation never moves money. Every action is initiated and explicitly confirmed by the authenticated customer, passes through the existing authorized transfer flow, and may require risk MFA. Limits, expiry, suspension, revocation, a persisted fail-closed operator kill switch, immutable audit events, idempotency, and notification evidence remain visible. There is no autonomous execution worker. Cross-currency forecasts use read-only decimal quotes with provider/as-of/provenance and fail-closed staleness handling; they never execute FX. See [the MVP design](design/2026-07-15-outcome-protection-money-debugger-design.md), [the production-readiness increment](design/2026-07-16-balance-shield-production-readiness.md), [the executable guardrail specification](design/2026-07-16-balance-shield-consent-guardrails.md), and [the operator runbook](operations/balance-shield-guardrail-runbook.md), and [the Outcome Types and Repair Search V2 design](design/2026-07-17-outcome-types-repair-search-v2.md).

Executable repairs also enforce the versioned [`outcome-source-v2` freshness invariant](design/2026-08-12-outcome-protection-source-freshness.md) at consent, activation, execution submission, and risk-MFA completion. HTTP 409 `SCENARIO_DIVERGED` means an authoritative balance/projection, account state, schedule, or protected obligation changed. The customer must refresh or re-run the scenario, select a newly replay-proven repair, and consent again. The rejection is append-only evidence with `moneyMoved=false`; it cannot create a transfer, hold, journal, schedule mutation, limit mutation, or autonomous action.

## Risk-based Step-up Authorization

Risky immediate transfers are paused until the customer verifies a TOTP authenticator code or a single-use recovery code. A challenged request creates only a pending authorization record: no transaction, balance movement, debit hold, or ledger journal is created before verification succeeds.

The default policy challenges a transfer when any of these signals applies:

- The amount is at least `5000`.
- An external destination was entered manually instead of selected from saved recipients.
- The selected recipient was created within the last 24 hours.
- The request would be the fifth completed transfer within 10 minutes.
- The source account was unfrozen within the last 24 hours.

Transfers between accounts owned by the same customer are not classified as manual external transfers, although another risk signal can still require verification. After successful verification, the account service issues a short-lived proof bound to the customer, exact transfer fingerprint, and authorization record. The transaction service consumes that proof once and executes the original idempotent transfer.

Customer flow:

1. Open `/security`, confirm the current password, and enroll an authenticator app.
2. Store the generated recovery codes offline; every recovery code is single-use.
3. Submit a transfer normally. Low-risk transfers continue immediately.
4. If challenged, enter an authenticator or recovery code in the verification panel.
5. The authorized transfer completes and appears in transaction history with its balanced ledger journal.

The policy is disabled by default so migrations can be deployed and customers can enroll before enforcement. See [Risk-based step-up authorization](risk-based-step-up-authorization.md) for operational details, policy tuning, and the live smoke test.
