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
- Deposit, withdraw, and transfer money.
- Generate an `Idempotency-Key` per money-movement submit.
- Search and filter transaction history.
- View transaction details and user transaction stats.

### Admin/Ops App

- Hide admin navigation for normal users.
- Guard admin routes using `ROLE_ADMIN` from JWT claims.
- Search accounts across users with owner and account type filters.
- Use existing account create/update/delete flows for admin oversight.
- Monitor account service health, metrics, deployment information, and manual health checks.
- Monitor transaction service health, transaction/system stats, alert status, and available metrics.
- Search operational transaction views.
- Reverse transactions using the backend reversal endpoint.
- View reversal-related status panels.

### Backend Services

- Account service:
  - Authentication and JWT issuance.
  - User registration.
  - Account CRUD.
  - Account type validation for checking, savings, and credit accounts.
  - Health, metrics, and deployment endpoints.
- Transaction service:
  - Deposit, withdrawal, and transfer endpoints.
  - Transaction history and search.
  - Transaction stats and monitoring endpoints.
  - Idempotency and reversal workflows.
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
DELETE /api/accounts/{id}
```

Admin account oversight uses:

```http
GET /api/accounts?ownerId=&accountType=&page=&size=
```

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
- Transaction table filters.
- Admin navigation visibility.
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

