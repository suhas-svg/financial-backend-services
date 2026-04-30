# Financial Console Frontend

React + Vite frontend for the financial backend services.

## Local Development

From this directory:

```powershell
npm install
npm run dev
```

The Vite dev server proxies browser requests so the frontend does not need direct CORS access to the backend ports:

- `/account-api/*` -> `http://localhost:8080/*`
- `/transaction-api/*` -> `http://localhost:8081/*`

Run the backend services first, then open the Vite URL printed by `npm run dev`.

## Implemented Areas

- Customer app:
  - Register and login with JWT session persistence in `sessionStorage`
  - Account list, create, type filter, and delete
  - Deposit, withdrawal, and transfer with generated `Idempotency-Key` headers
  - Transaction history, search filters, detail view, limits, and user stats
- Admin app:
  - Admin-only navigation based on JWT `roles`
  - Cross-user account search
  - Monitoring panels for account and transaction service endpoints
  - Operations transaction search and reversal workflow

## Production Deployment Note

Use one of these deployment shapes:

1. Serve the frontend behind the same reverse proxy as the services and route `/account-api` and `/transaction-api` to the two Spring Boot services.
2. Add explicit CORS configuration to both Spring services for the frontend origin.

The first option is preferred because it keeps the browser-facing API shape identical to development and avoids broad CORS rules.

## Admin Testing Note

The backend registration flow creates `ROLE_USER` accounts only. To test Phase 2 admin screens, seed or promote a user with `ROLE_ADMIN` in the account-service database, then log in as that user.

## Verification

```powershell
npm test
npm run build
```
