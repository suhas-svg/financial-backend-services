# Customer Spending Controls

The MVP adds customer-owned daily transfer and withdrawal limits per account. Customers manage them on `/security`.

## Behavior

- New accounts use defaults of USD 10,000 for transfers and USD 2,000 for withdrawals until customized.
- Reductions apply immediately and replace any pending increase.
- An increase requires an active MFA method plus a valid authenticator or recovery code. It becomes effective after 24 hours.
- Daily usage resets by the account service's calendar date.
- Transaction service reserves usage through an idempotent internal request after account ownership and status checks, but before creating a transaction, placing a hold, moving a balance, or posting a ledger journal.
- If downstream hold, balance, or ledger processing fails, transaction service idempotently releases that reservation so failed operations do not consume the customer's allowance.
- Account-level database locking serializes concurrent reservations. `(account, operation type, idempotency key)` is unique.
- At 80% usage a deduplicated warning is emitted. Rejections and configuration changes also create in-app notifications.
- Limit changes, reservations, threshold warnings, and rejections are recorded in `spending_limit_audit_events`. Privileged callers can inspect the latest events at `GET /api/internal/accounts/spending-limit-audit`.

## APIs

- `GET /api/security/spending-limits`
- `PUT /api/security/spending-limits/{accountId}`
- `POST /api/internal/accounts/{accountId}/spending-limit-reservations`
- `DELETE /api/internal/accounts/{accountId}/spending-limit-reservations/{operationType}/{idempotencyKey}?userId=`
- `GET /api/internal/accounts/spending-limit-audit`

The customer update body contains `transferDailyLimit`, `withdrawalDailyLimit`, and optional `credential`. The credential is required only when either effective limit increases.

This MVP does not implement budgets, parental controls, business roles, or automated fraud decisions.
