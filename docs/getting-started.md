# Getting started

Run the backend and the frontend locally, and the configuration they read.

## Local Development

### Prerequisites

- Java 21 or 22.
- Node.js 20 or newer.
- Docker Desktop for the compose-based backend path.
- PostgreSQL if running the services outside Docker.

### Start Backend Services

The frontend expects:

- Account service: `http://127.0.0.1:8080`
- Transaction service: `http://127.0.0.1:8081`

The verified Docker path uses `docker-compose.dev.yml` for the complete backend stack and `docker-compose.dev.override.yml` to expose both PostgreSQL instances on loopback-only host ports. Set local-only signing secrets before starting the stack; do not reuse these example values outside local development:

```powershell
$env:JWT_SECRET = "<set-via-secret-manager>"
$env:INTERNAL_JWT_SECRET = "<set-via-secret-manager>"
$env:MFA_ENCRYPTION_KEY = "local-development-mfa-encryption-key-change-me-at-least-32-characters"
$env:STEP_UP_ENABLED = "true"
# Optional: allow synthetic customer deposits so a fresh local stack can be funded.
$env:CUSTOMER_DEPOSITS_ENABLED = "true"
docker compose -f docker-compose.dev.yml -f docker-compose.dev.override.yml up --build -d
docker compose -f docker-compose.dev.yml -f docker-compose.dev.override.yml ps
```

Wait for `account-service` and `transaction-service` to report healthy. To stop the stack while preserving its database volumes:

```powershell
docker compose -f docker-compose.dev.yml -f docker-compose.dev.override.yml down
```

For manual JVM startup instead, provide PostgreSQL, matching JWT secrets, and the service-specific configuration, then run:

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

Authenticated customer pages use these routes:

- `/login` - customer sign-in, with an Operations sign-in mode at `/login?portal=admin`
- `/` - dashboard
- `/accounts` - customer accounts
- `/move-money` - deposits, withdrawals, and transfers
- `/scheduled-transfers` - scheduled and recurring transfers
- `/outcome-protection` - Balance Shield scenarios, reverse-stress proof, consent/MFA lifecycle, and explicit guardrail actions
- `/transactions` - transaction history, detail, disputes, and reversals
- `/disputes` - submitted dispute history
- `/notifications` - notification inbox
- `/security` - authenticator enrollment, recovery codes, and MFA management

Admin pages are protected by `ROLE_ADMIN` and live under `/admin`:

- `/admin` - operations overview
- `/admin/accounts`
- `/admin/monitoring`
- `/admin/transactions`
- `/admin/audit-log`
- `/admin/risk-alerts`
- `/admin/risk-cases`
- `/admin/disputes`
- `/admin/investigations`
- `/admin/reconciliation`

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

Risk-based step-up authorization uses these environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `MFA_ENCRYPTION_KEY` | empty | Private key material used by account-service to encrypt authenticator secrets at rest. Use at least 32 random characters and plan key rotation carefully. |
| `STEP_UP_ENABLED` | `false` | Enables transfer policy enforcement. |
| `CUSTOMER_DEPOSITS_ENABLED` | `false` | Allows controlled synthetic customer deposits in the local Compose stack. Keep `false` until a funding provider is activated. |
| `STEP_UP_HIGH_VALUE_THRESHOLD` | `5000.00` | Transfer amount that triggers the high-value signal. |
| `STEP_UP_BENEFICIARY_COOLING_HOURS` | `24` | Age window for newly saved recipients. |
| `STEP_UP_RAPID_TRANSFER_WINDOW_MINUTES` | `10` | Lookback window for rapid-transfer detection. |
| `STEP_UP_RAPID_TRANSFER_COUNT` | `5` | Completed-transfer count that triggers verification. |
| `STEP_UP_RECENT_UNFREEZE_HOURS` | `24` | Risk window after a source account is reactivated. |

Challenges expire after five minutes and authorization proofs after two minutes by default. Account-service also supports `STEP_UP_CHALLENGE_TTL_SECONDS`, `STEP_UP_PROOF_TTL_SECONDS`, and `STEP_UP_MAX_ATTEMPTS`. Never commit the MFA encryption key or other deployment secrets.

Redis is optional for manual local JVM runs of `transaction-service`. The default local configuration disables Redis health so core transaction flows can run with only PostgreSQL and account-service. Compose, E2E, and Helm deployment configs explicitly enable Redis health because those environments provision Redis.

For an externally approved deployment, use one of these frontend patterns:

- Serve the frontend behind a reverse proxy and route `/account-api` and `/transaction-api` to the Spring services.
- Or configure explicit CORS rules on both Spring services for the deployed frontend origin.

The reverse-proxy option is preferred because it keeps browser-facing URLs consistent with local development.
