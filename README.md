# Financial Backend Services

Financial Backend Services is a banking-style application made of two Spring Boot backend services and a React frontend console.

The backend provides account management, authentication, transaction processing, monitoring, and reversal workflows. The frontend in `frontend/` provides the customer banking app first, then the Phase 2 admin and operations dashboard.

## Project Layout

```text
financial-backend-services/
├── account-service/          # Spring Boot account/auth service on port 8080
├── transaction-service/      # Spring Boot transaction service on port 8081
├── frontend/                 # React + Vite + TypeScript financial console
├── docker-compose.codex.yml  # Local verification compose file used during this work
├── .github/workflows/        # PR validation and CI checks
└── README.md
```

## Main Features

### Customer App

- Register, login, and logout with JWT-backed sessions.
- Store the JWT in memory and `sessionStorage` for reload survival.
- Decode JWT roles client-side for route guards and navigation.
- View dashboard totals, account cards, recent transactions, limits, and personal stats.
- Create, edit, delete, and filter accounts.
- Create `CHECKING`, `SAVINGS`, and `CREDIT` accounts with type-specific validation.
- See frozen accounts with hold warnings and status reasons.
- Deposit, withdraw, and transfer money.
- Keep frozen accounts selectable for credits, while debit-source controls are disabled.
- Generate an `Idempotency-Key` per money-movement submit.
- Search and filter transaction history.
- View transaction details and user transaction stats.

### Admin/Ops App

- Hide admin navigation for normal users.
- Guard admin routes using `ROLE_ADMIN` from JWT claims.
- Search accounts across users with owner and account type filters.
- Filter accounts by lifecycle status and freeze or unfreeze accounts with required reasons.
- Use existing account create/update/delete flows for admin oversight.
- Monitor account service health, metrics, deployment information, and manual health checks.
- Monitor transaction service health, transaction/system stats, alert status, and available metrics.
- Search operational transaction views.
- Reverse transactions using the backend reversal endpoint.
- View reversal-related status panels.
- Review the transaction-service audit log with admin-only summary counters, filters, event search, and selected-event details.
- Review transaction risk alerts with summary counters, filters, detail inspection, and review/dismiss/escalate actions.
- Reconstruct investigation context across transactions, audit events, risk alerts, risk cases, and case notes.
- Print investigation reports with current filters, key findings, and a timeline preview.
- Export investigation timelines as admin-only CSV files using the same investigation filters used by the timeline view.

### Backend Services

- Account service:
  - Authentication and JWT issuance.
  - User registration.
  - Account CRUD.
  - Account lifecycle status with `ACTIVE` and `FROZEN` states.
  - Admin-only status updates with reason and updated-by metadata.
  - Frozen-account debit rejection while preserving deposits and incoming credits.
  - Account type validation for checking, savings, and credit accounts.
  - Health, metrics, and deployment endpoints.
- Transaction service:
  - Deposit, withdrawal, and transfer endpoints.
  - Frozen-account debit enforcement before withdrawals and outgoing transfer debits.
  - Transaction history and search.
  - Transaction stats and monitoring endpoints.
  - Idempotency and reversal workflows.
  - Persistent audit log storage for high-value transaction and security events.
  - Admin-only audit log search, detail, and summary APIs.
  - Persistent risk alert review queue for conservative transaction risk rules.
  - Admin-only risk alert search, detail, summary, and status update APIs.
  - Admin-only investigation timeline, summary, and CSV export APIs.
  - Account-service integration for balance updates.

## Technology Stack

### Backend

- Java 21/22 compatible Spring Boot services.
- Maven wrappers per service.
- Spring Security with JWT.
- Spring Data JPA.
- Flyway migrations.
- PostgreSQL.
- Micrometer and Spring Boot Actuator monitoring.
- JUnit 5, Mockito, and integration tests.

### Frontend

- React 18.
- Vite.
- TypeScript.
- React Router.
- TanStack Query.
- React Hook Form.
- Zod.
- Tailwind CSS.
- Lucide icons.
- Vitest and Testing Library.
- Playwright E2E tests.

## Local Development

### Prerequisites

- Java 21 or 22.
- Node.js 20 or newer.
- Docker Desktop for the compose-based backend path.
- PostgreSQL if running the services outside Docker.

### Start Backend Services

The frontend expects:

- Account service: `http://localhost:8080`
- Transaction service: `http://localhost:8081`

Use your normal service startup, or the project compose files where appropriate. For the service folders:

```powershell
cd account-service
.\mvnw.cmd spring-boot:run
```

```powershell
cd transaction-service
.\mvnw.cmd spring-boot:run
```

### Start Frontend

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite URL printed in the terminal, normally `http://127.0.0.1:5173`.

The Vite dev server proxies browser requests through:

- `/account-api/*` -> `http://localhost:8080/*`
- `/transaction-api/*` -> `http://localhost:8081/*`

That means the browser does not call backend ports directly during local development.

## Configuration

Use environment-provided secrets. Do not commit real JWT secrets.

Example backend configuration shape:

```properties
security.jwt.secret=${JWT_SECRET}
security.jwt.expiration-in-ms=3600000
```

For service-to-service calls, keep the same JWT signing configuration across both services.

Redis is optional for manual local JVM runs of `transaction-service`. The default local configuration disables Redis health so core transaction flows can run with only PostgreSQL and account-service. Compose, E2E, and Helm deployment configs explicitly enable Redis health because those environments provision Redis.

For production frontend deployment, use one of these patterns:

- Serve the frontend behind a reverse proxy and route `/account-api` and `/transaction-api` to the Spring services.
- Or configure explicit CORS rules on both Spring services for the deployed frontend origin.

The reverse-proxy option is preferred because it keeps browser-facing URLs consistent with local development.

## API Surface Used By Frontend

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
DELETE /api/accounts/{id}
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

### Investigation Timeline

The admin Investigations page is a read-only workspace for reconstructing what happened across transaction, audit, risk alert, and risk case records. Admins can search by user, transaction, account, alert, or case identifiers and review a single chronological timeline with linked metadata.

Investigation APIs use the `/transaction-api/api/investigations/*` frontend proxy and require `ROLE_ADMIN` or `ROLE_INTERNAL_SERVICE`.

```http
GET /api/investigations/timeline?userId=&transactionId=&accountId=&alertId=&caseId=&from=&to=&page=&size=
GET /api/investigations/summary?userId=&transactionId=&accountId=&alertId=&caseId=&from=&to=
GET /api/investigations/export?userId=&transactionId=&accountId=&alertId=&caseId=&from=&to=
```

Timeline items include `TRANSACTION`, `AUDIT_EVENT`, `RISK_ALERT`, `RISK_CASE`, and `CASE_NOTE` records. Searches by `caseId` expand to the case user, transaction, and linked alert context; searches by `alertId` expand to the alert user and transaction context.

The page also builds a report panel from the current filters. It summarizes the report scope, flags high-severity investigation activity, previews the first timeline items, and supports browser printing through the `Print report` action. `GET /api/investigations/export` returns a `text/csv` attachment named `investigation-export.csv` with the same filtered timeline data and escaped metadata JSON for offline review.

Version 1 is read-only and does not update alerts, cases, accounts, or transactions.

## Testing

### Frontend

```powershell
cd frontend
npm test
npm run build
npm run e2e
```

The frontend test suite covers:

- API proxy/client behavior.
- JWT session restore and role extraction.
- Form schemas for auth, accounts, money movement, and reversals.
- Login/register success and failure states.
- Account type-specific fields.
- Account status badges, frozen warnings, admin status filters, and freeze/unfreeze reason validation.
- Transaction table filters.
- Admin navigation visibility.
- Admin audit log summary, filters, event table, detail panel, and API proxy mapping.
- Admin risk alert summary, filters, queue table, detail panel, status actions, and API proxy mapping.
- Admin risk case summary, filters, queue table, detail panel, claim/status/note actions, create-from-alert action, and API proxy mapping.
- Admin investigation summary, search controls, report preview, print action, CSV export flow, mixed-source timeline, detail panel, and API proxy mapping.
- Customer and admin Playwright flows.

### Backend

```powershell
cd account-service
.\mvnw.cmd -q test
```

```powershell
cd transaction-service
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q -Dtest=TransactionServiceHardeningTest test
```

The transaction-service test suite also covers the admin audit controller, audit persistence rules, audit filtering, summary counts, and 90-day cleanup. Risk alert tests cover admin-only access, filters, summary counts, status updates with reviewer metadata, rule generation, dedupe behavior, and non-risky transaction handling. Risk case tests cover admin-only access, filters, summary counts, create-from-alert, duplicate open-case handling, claim, status updates, linked alert details, and append-only notes. Investigation tests cover admin-only access, search context expansion, mixed-source timeline sorting, summary counts, CSV export headers/content escaping, and empty search results.

Account hold/freeze tests cover default `ACTIVE` accounts, admin-only freeze/unfreeze with required reasons, frozen debit rejection, credit allowance, transaction-service prechecks, backend rejection messages, frontend status rendering, and move-money debit-source disabling.

## Demo Evidence

The PR includes screenshot artifacts under:

```text
frontend/demo-screenshots/
frontend/backend-demo-screenshots/
```

These show the customer app, admin dashboard, monitoring page, backend smoke checks, account creation validation, money movement, and transaction history flows.

## Admin Testing Note

The public registration flow creates normal `ROLE_USER` accounts. To test Phase 2 admin screens against the real backend, seed or promote a user with `ROLE_ADMIN` in the account-service database before logging in.

## CI Notes

The PR validates:

- Account service tests on Java 21 and Java 22.
- Transaction service hardening tests on Java 21 and Java 22.
- Production config policy checks.
- Secret scanning.
- PR compile checks.

Maven wrapper scripts must keep executable permissions for Ubuntu CI:

```text
account-service/mvnw
transaction-service/mvnw
```

## Additional Documentation

- Frontend-specific setup: `frontend/README.md`
- Transaction history API notes: `transaction-service/TRANSACTION-HISTORY-API.md`
- Monitoring and observability notes: `transaction-service/MONITORING-OBSERVABILITY-GUIDE.md`

