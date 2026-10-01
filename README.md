# Financial Backend Services

A banking-style product: a customer app and an operations console (React) on top of two
Spring Boot services that own accounts and money movement. Money is tracked in a double-entry
ledger, every movement is idempotent, and risky transfers require step-up authentication.

The product runs as a **controlled synthetic beta**: no real money moves. What must be true
before that changes is written down in the
[real-money readiness gate](docs/operations/real-money-production-readiness-gate.md).

## What it does

- **Customers**: accounts and balances (ledger, available, pending), deposits, withdrawals,
  transfers, saved beneficiaries, scheduled and recurring transfers, statements, disputes,
  notifications, TOTP and recovery codes, and Balance Shield, which finds the smallest change
  that keeps upcoming payments from failing and can act on it with consent.
- **Operators**: account freeze/unfreeze, transaction and audit search, risk alerts and cases,
  dispute queue, investigation timelines with CSV export, ledger reconciliation.
- **Under the hood**: debit holds before every debit, frozen-account enforcement, risk-based
  step-up for transfers, database-backed login throttling, short-lived access tokens with
  rotating refresh sessions, and background jobs that are safe on several replicas.

Details: [docs/features.md](docs/features.md).

## Layout

```text
account-service/       Spring Boot: auth, MFA/step-up, accounts, holds, notifications (:8080)
transaction-service/   Spring Boot: money movement, ledger, schedules, risk, disputes (:8081)
frontend/              React + Vite + TypeScript customer app and operations console
infrastructure/        Helm charts, Terraform, Prometheus SLO rules, sandbox gateway
docs/                  Product, API, operations and design documentation
scripts/               Release-authority policy, sandbox drills, evidence tooling
e2e-tests/             Quarantined legacy E2E harness (kept fail-closed by policy)
financial-mcp-server/  Archived, unsupported
```

## Quick start

Requires Java 21+, Node 20+ and Docker.

```bash
docker compose -f docker-compose.dev.yml -f docker-compose.dev.override.yml up --build -d
cd frontend && npm install && npm run dev
```

Set the signing secrets first and open <http://127.0.0.1:5173>. The full walkthrough,
including running the services outside Docker and every configuration variable, is in
[docs/getting-started.md](docs/getting-started.md).

The **synthetic sandbox** (`docker-compose.synthetic-sandbox.yml`) is the supported,
production-shaped way to run everything behind a TLS gateway:
[docs/operations/controlled-beta-phase2-sandbox.md](docs/operations/controlled-beta-phase2-sandbox.md).

## Stack

Java 21 · Spring Boot 3.5 · Spring Security (JWT) · JPA + Flyway · PostgreSQL · Redis ·
Micrometer + OpenTelemetry · React 18 · TanStack Query · React Hook Form + Zod · Tailwind ·
Vitest · Playwright · Helm · Terraform.

## Quality gates

`main` accepts changes only through one required check, **Required Acceptance**: both services
on Java 21 and 22, fresh-database migrations, concurrency and recovery regressions, frontend
tests and WCAG accessibility, the full sandbox API/browser contract, Helm/Terraform policy,
container and dependency scans, SBOMs and a full-history secret scan.
See [docs/testing.md](docs/testing.md).

## Documentation

| Topic | Where |
| --- | --- |
| Features in depth | [docs/features.md](docs/features.md) |
| Local development and configuration | [docs/getting-started.md](docs/getting-started.md) |
| API reference | [docs/api/](docs/api/) |
| Testing and CI | [docs/testing.md](docs/testing.md) |
| Deployment boundaries | [docs/deployment-authority.md](docs/deployment-authority.md) |
| Observability, tracing and SLOs | [docs/operations/observability.md](docs/operations/observability.md) |
| Operations runbooks | [docs/operations/](docs/operations/) |
| Financial integrity contracts | [docs/controlled-beta-phase1-integrity.md](docs/controlled-beta-phase1-integrity.md) |
| Step-up authorization | [docs/risk-based-step-up-authorization.md](docs/risk-based-step-up-authorization.md) |
| Spending controls | [docs/customer-spending-controls.md](docs/customer-spending-controls.md) |
| Design records | [docs/design/](docs/design/) |
| Developer tooling (pre-commit, quality tools) | [DEVELOPMENT.md](DEVELOPMENT.md) |
| Frontend specifics | [frontend/README.md](frontend/README.md) |
