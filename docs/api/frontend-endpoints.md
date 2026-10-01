# API surface used by the frontend

A readable tour of the endpoints the consoles call. The OpenAPI specs in this folder
(`account-service.openapi.json`, `transaction-service.openapi.json`, see [README](README.md))
are the complete, authoritative reference.

## Endpoints by area

### Auth

```http
POST /api/auth/register
POST /api/auth/login
```

### Accounts

```http
GET    /api/accounts
POST   /api/accounts
GET    /api/accounts/{id}
PUT    /api/accounts/{id}
PATCH  /api/accounts/{id}/status
```

Customer hard deletion is not supported. Ledger-authoritative closure preserves
the account and its financial history:

```http
POST /api/controlled-beta/accounts/{accountId}/close
```

Admin account oversight uses:

```http
GET /api/accounts?ownerId=&accountType=&status=&page=&size=
```

`PATCH /api/accounts/{id}/status` requires `ROLE_ADMIN` and accepts:

```json
{
  "status": "FROZEN | ACTIVE",
  "reason": "required status reason"
}
```

`FROZEN` blocks debits only: withdrawals and outgoing transfer debits are rejected. Deposits and incoming transfer credits remain allowed.

### Internal Account Balance And Holds

`transaction-service` owns the authoritative double-entry ledger. `account-service`
stores a delivered projection for account views and hold enforcement. Public
account JSON keeps `balance` as a compatibility alias for `ledgerBalance`, while
newer clients can read both:

```json
{
  "balance": 800.00,
  "ledgerBalance": 800.00,
  "availableBalance": 800.00
}
```

Debit holds reserve spendable funds before a debit completes:

- `PLACED`: decreases `availableBalance` only.
- `CAPTURED`: decreases `ledgerBalance`; available stays unchanged because funds were already reserved.
- `RELEASED`: restores `availableBalance`; ledger stays unchanged.

Internal service-to-service endpoints require `ROLE_INTERNAL_SERVICE` or `ROLE_ADMIN`:

```http
POST /api/internal/accounts/{id}/holds
POST /api/internal/accounts/{id}/holds/{holdId}/capture
POST /api/internal/accounts/{id}/holds/{holdId}/release
```

Place hold body:

```json
{
  "holdId": "transaction-id:hold",
  "transactionId": "transaction-id",
  "amount": 200.00,
  "reason": "WITHDRAWAL_HOLD"
}
```

Capture and release body:

```json
{
  "transactionId": "transaction-id",
  "reason": "WITHDRAWAL_CAPTURE"
}
```

Hold placement is rejected when the account is `FROZEN` or `availableBalance` is too low. Duplicate place/capture/release requests are idempotent when they match the original hold data.

### Transactions

```http
GET  /api/transactions
GET  /api/transactions/search
GET  /api/transactions/{transactionId}
GET  /api/transactions/user/stats
POST /api/transactions/deposit
POST /api/transactions/withdraw
POST /api/transactions/transfer
POST /api/transactions/{transactionId}/reverse
```

Risk-based transfer authorization extends the transfer response with authorization state. A challenged transfer returns a pending authorization instead of a completed transaction; submit its code to the authorization endpoint to continue the original request:

```http
POST /api/transactions/transfer
POST /api/transactions/{authorizationId}/authorize
DELETE /api/transactions/{authorizationId}/authorization
```

MFA lifecycle endpoints are owned by account-service and always act on the authenticated customer:

```http
GET  /api/security/mfa
POST /api/security/mfa/totp/enroll
POST /api/security/mfa/totp/confirm
POST /api/security/mfa/recovery-codes/regenerate
DELETE /api/security/mfa/totp
```

Enrollment and destructive MFA changes require the current password. Verification accepts an authenticator code or unused recovery code; recovery codes are stored as hashes and returned only when generated.

Scheduled transfer customer routes use the authenticated user and are exposed through the frontend transaction proxy:

```http
POST   /api/scheduled-transfers
GET    /api/scheduled-transfers?page=&size=&status=
GET    /api/scheduled-transfers/{scheduleId}
PATCH  /api/scheduled-transfers/{scheduleId}/pause
PATCH  /api/scheduled-transfers/{scheduleId}/resume
DELETE /api/scheduled-transfers/{scheduleId}
GET    /api/scheduled-transfers/{scheduleId}/runs?page=&size=
```

Debit transaction behavior:

- Deposits use no hold and increase both ledger and available balances.
- Withdrawals use `place hold -> capture hold -> complete transaction`.
- Outgoing transfers use holds on the source account, then credit the destination account.
- Incoming transfer credits may target a frozen account because credits are allowed.
- If hold placement or capture fails, the transaction is marked `FAILED` and the failure is audited.
- If destination credit fails after source capture, transaction-service uses the existing compensation path to credit the source account back.

Processing states include `HOLD_PLACED`, `HOLD_CAPTURED`, and `HOLD_RELEASED`. Audit events include `DEBIT_HOLD_PLACED`, `DEBIT_HOLD_CAPTURED`, `DEBIT_HOLD_RELEASED`, and `DEBIT_HOLD_REJECTED`.

### Monitoring

Account service:

```http
GET  /api/health/status
GET  /api/health/metrics
GET  /api/health/deployment
POST /api/health/check
```

Transaction service:

```http
GET /api/monitoring/health/detailed
GET /api/monitoring/stats/transactions
GET /api/monitoring/stats/system
GET /api/monitoring/alerts/status
GET /api/monitoring/metrics/available
```

### Audit Log

The admin Audit Log page calls the transaction-service audit APIs through the frontend proxy at `/transaction-api/api/audit/*`.

Audit APIs require `ROLE_ADMIN` or `ROLE_INTERNAL_SERVICE`.

```http
GET /api/audit/events?page=&size=&eventType=&action=&outcome=&userId=&transactionId=&from=&to=
GET /api/audit/events/{eventId}
GET /api/audit/summary?from=&to=
```

Version 1 stores transaction initiated, completed, failed, reversed, and security events in `transaction-service`. Audit rows are retained for 90 days and intentionally exclude stack traces, JWTs, passwords, authorization headers, and raw token values.

Financial transaction and security audit events are persisted synchronously with their owning workflow. Generic `API_ACCESS` evidence is dispatched to a bounded audit executor so routine dashboard polling does not extend customer response latency. The executor drains for up to 30 seconds during graceful shutdown and uses caller-runs backpressure when saturated or rejected, preserving evidence rather than silently dropping it.

Scheduled-transfer lifecycle notifications are submitted to a bounded executor only after the authoritative schedule transaction commits. Notification-provider latency or failure cannot roll back schedule state; executor saturation applies backpressure, and delivery failures are logged without exposing customer data.

### Risk Alerts

The admin Risk Alerts page calls the transaction-service risk APIs through the frontend proxy at `/transaction-api/api/risk/*`.

Risk APIs require `ROLE_ADMIN` or `ROLE_INTERNAL_SERVICE`.

```http
GET   /api/risk/alerts?page=&size=&status=&severity=&alertType=&userId=&transactionId=&from=&to=
GET   /api/risk/alerts/{alertId}
GET   /api/risk/summary?from=&to=
PATCH /api/risk/alerts/{alertId}/status
```

`PATCH /api/risk/alerts/{alertId}/status` accepts:

```json
{
  "status": "REVIEWED | DISMISSED | ESCALATED",
  "resolutionNote": "short admin note"
}
```

Version 1 creates operational review records only; it does not block transactions, reverse transactions, or lock accounts automatically.

Default built-in rules:

- `HIGH_VALUE_TRANSFER`: completed transfer amount greater than or equal to `5000`, severity `HIGH`.
- `REPEATED_FAILURES`: at least `3` failed transactions by the same user in `15` minutes, severity `MEDIUM`.
- `RAPID_TRANSFERS`: at least `5` completed transfers by the same user in `10` minutes, severity `MEDIUM`.
- `REVERSAL_HEAVY_ACTIVITY`: at least `2` reversals by the same user in `24` hours, severity `HIGH`.

Open alerts use a `dedupeKey` so repeated evaluations do not create duplicate open alerts for the same rule/user/transaction/window.

### Risk Case Management

The admin Risk Cases page builds an internal case workflow on top of Risk Alerts. Cases are created manually from a selected alert, start unassigned, and can be claimed by an admin for review. Version 1 keeps cases operational only: it does not message customers, lock accounts, reverse transactions, or make automated fraud decisions.

Case APIs use the same `/transaction-api/api/risk/*` frontend proxy and require `ROLE_ADMIN` or `ROLE_INTERNAL_SERVICE`.

```http
GET   /api/risk/cases?page=&size=&status=&priority=&assignedTo=&userId=&transactionId=&alertId=&from=&to=
GET   /api/risk/cases/{caseId}
GET   /api/risk/cases/summary?from=&to=
POST  /api/risk/cases/from-alert/{alertId}
PATCH /api/risk/cases/{caseId}/claim
PATCH /api/risk/cases/{caseId}/status
POST  /api/risk/cases/{caseId}/notes
```

Case statuses are `OPEN`, `IN_REVIEW`, `RESOLVED`, and `CLOSED`. Priorities are `LOW`, `MEDIUM`, `HIGH`, and `CRITICAL`; when omitted at creation time, alert severity maps to case priority (`HIGH` -> `HIGH`, `MEDIUM` -> `MEDIUM`, fallback `LOW`). Notes are internal, append-only admin notes.

Example create/status/note bodies:

```json
{
  "title": "Review high-value transfer",
  "priority": "HIGH",
  "reason": "Manual review requested by admin"
}
```

```json
{
  "status": "RESOLVED",
  "resolutionNote": "Reviewed transaction history and no further action required."
}
```

```json
{
  "note": "Customer transaction pattern looks unusual compared with prior activity."
}
```

### Transaction Disputes

Customers can dispute their own `COMPLETED` transactions from the last 60 days. Version 1 creates operational dispute records only: approving a dispute does not automatically reverse, refund, lock, or move money.

Customer dispute APIs use the `/transaction-api/api/disputes/*` frontend proxy and require an authenticated user:

```http
POST /api/disputes
GET  /api/disputes?page=&size=
GET  /api/disputes/{disputeId}
```

`POST /api/disputes` accepts:

```json
{
  "transactionId": "transaction-id",
  "reasonCode": "UNAUTHORIZED",
  "description": "Customer explanation with enough detail for review."
}
```

Supported reason codes are `UNAUTHORIZED`, `DUPLICATE`, `INCORRECT_AMOUNT`, `SERVICE_NOT_RECEIVED`, and `OTHER`. The backend rejects disputes for non-owned transactions, non-`COMPLETED` transactions, transactions older than 60 days, and transactions that already have an active dispute.

Admin dispute APIs require `ROLE_ADMIN` or `ROLE_INTERNAL_SERVICE`:

```http
GET   /api/disputes/admin?page=&size=&status=&userId=&transactionId=&reasonCode=&assignedTo=&from=&to=
GET   /api/disputes/admin/summary?from=&to=
PATCH /api/disputes/admin/{disputeId}/claim
PATCH /api/disputes/admin/{disputeId}/status
POST  /api/disputes/admin/{disputeId}/notes
```

Dispute statuses are `OPEN`, `IN_REVIEW`, `APPROVED`, `DENIED`, and `CLOSED`. Claiming a dispute assigns it to the current admin and moves `OPEN` disputes to `IN_REVIEW`. `APPROVED`, `DENIED`, and `CLOSED` set `closedAt`. Notes are internal, append-only admin notes.

Example status and note bodies:

```json
{
  "status": "APPROVED",
  "resolutionNote": "Customer claim accepted after review."
}
```

```json
{
  "note": "Reviewed transaction logs and customer account history."
}
```

### Investigation Timeline

The admin Investigations page is a read-only workspace for reconstructing what happened across transaction, audit, risk alert, risk case, and dispute records. Admins can search by user, transaction, account, alert, or case identifiers and review a single chronological timeline with linked metadata.

Investigation APIs use the `/transaction-api/api/investigations/*` frontend proxy and require `ROLE_ADMIN` or `ROLE_INTERNAL_SERVICE`.

```http
GET /api/investigations/timeline?userId=&transactionId=&accountId=&alertId=&caseId=&from=&to=&page=&size=
GET /api/investigations/summary?userId=&transactionId=&accountId=&alertId=&caseId=&from=&to=
GET /api/investigations/export?userId=&transactionId=&accountId=&alertId=&caseId=&from=&to=
```

Timeline items include `TRANSACTION`, `AUDIT_EVENT`, `RISK_ALERT`, `RISK_CASE`, `CASE_NOTE`, `DISPUTE`, and `DISPUTE_NOTE` records. Searches by `caseId` expand to the case user, transaction, and linked alert context; searches by `alertId` expand to the alert user and transaction context. Searches by `userId` or `transactionId` include matching disputes and dispute notes.

The page also builds a report panel from the current filters. It summarizes the report scope, flags high-severity investigation activity, previews the first timeline items, and supports browser printing through the `Print report` action. `GET /api/investigations/export` returns a `text/csv` attachment named `investigation-export.csv` with the same filtered timeline data and escaped metadata JSON for offline review.

Version 1 is read-only and does not update alerts, cases, accounts, or transactions.
