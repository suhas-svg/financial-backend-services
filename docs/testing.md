# Testing and CI

How to run each test suite, and what the protected `Required Acceptance` check runs.

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
- In-memory JWT handling and role extraction.
- Form schemas for auth, accounts, money movement, and reversals.
- Login/register success and failure states.
- Account type-specific fields.
- Account status badges, frozen warnings, admin status filters, and freeze/unfreeze reason validation.
- Available balance as the primary dashboard/account-card balance.
- Ledger and available balance display in customer and admin account views.
- Move Money debit-source disabling based on frozen status and insufficient available balance.
- Deposit and incoming-credit destination behavior remaining selectable for frozen or held accounts.
- Transaction table filters.
- Customer scheduled transfer route, account-backed create form, recurrence validation, proxy mapping, pause/resume/cancel actions, and run-history display.
- Customer dispute submission, validation, history, and resolution note display.
- Admin navigation visibility.
- Admin audit log summary, filters, event table, detail panel, and API proxy mapping.
- Admin risk alert summary, filters, queue table, detail panel, status actions, and API proxy mapping.
- Admin risk case summary, filters, queue table, detail panel, claim/status/note actions, create-from-alert action, and API proxy mapping.
- Admin dispute summary, filters, queue table, detail panel, claim/status/note actions, and API proxy mapping.
- Admin investigation summary, search controls, report preview, print action, CSV export flow, mixed-source timeline, detail panel, and API proxy mapping.
- Customer and admin Playwright flows.

### Backend

```powershell
cd account-service
.\mvnw.cmd -q test
```

```powershell
cd transaction-service
.\mvnw.cmd -q test
```

The transaction-service test suite also covers scheduled transfer persistence constraints, authenticated scheduled transfer APIs, scheduler claim/finalize behavior, execution recovery, notification emission, the admin audit controller, audit persistence rules, audit filtering, summary counts, and 90-day cleanup. Risk alert tests cover admin-only access, filters, summary counts, status updates with reviewer metadata, rule generation, dedupe behavior, and non-risky transaction handling. Risk case tests cover admin-only access, filters, summary counts, create-from-alert, duplicate open-case handling, claim, status updates, linked alert details, and append-only notes. Dispute tests cover customer ownership checks, completed/60-day eligibility, duplicate active-dispute rejection, admin listing, claim, status updates, internal notes, and investigation timeline/summary integration. Investigation tests cover admin-only access, search context expansion, mixed-source timeline sorting, summary counts, CSV export headers/content escaping, and empty search results.

Account hold/freeze tests cover default `ACTIVE` accounts, admin-only freeze/unfreeze with required reasons, frozen debit rejection, credit allowance, transaction-service prechecks, backend rejection messages, frontend status rendering, and move-money debit-source disabling.

Pending debit authorization tests cover account ledger/available initialization, migration backfill, hold placement/capture/release balance effects, idempotent hold transitions, frozen and insufficient-available hold rejection, deposit balance updates, withdrawal and transfer hold orchestration, failed hold audit behavior, compensation after destination credit failure, backward-compatible account DTO handling, and frontend available-balance rendering and validation.

Step-up authorization tests cover TOTP verification, encrypted secret storage, one-time recovery codes, challenge expiry and attempt limits, transfer fingerprint binding, risk-policy signals, pending authorization persistence, retry idempotency, controller behavior, customer Security UI, challenged-transfer verification, and ledger amount rendering for own-account transfers.

For a disposable live Docker smoke test with step-up enabled:

```powershell
.\scripts\test-step-up-authorization.ps1
```

The script confirms that funds and journals remain unchanged before authorization, then completes a high-value transfer and verifies balances, transaction state, and ledger postings.

## CI: the `Required Acceptance` check

`main` is protected by one required check, `Required Acceptance`, defined in
[`.github/workflows/release-authority.yml`](../.github/workflows/release-authority.yml)
(the only workflow; `scripts/verify-release-authority.ps1` enforces that). It passes only when
every gate passes:

| Gate | What it runs |
| --- | --- |
| Acceptance Authority Policy | workflow, deployment-authority and evidence-gate policy self-tests |
| Frontend Lint, Tests, Build | ESLint, Vitest, production build, initial-bundle check |
| Accessibility (WCAG 2.1 AA) | axe checks on public and signed-in pages of the production build |
| Service verification | `./mvnw clean verify` for both services on Java 21 and 22 (tests, PMD, SpotBugs, coverage) |
| Fresh PostgreSQL Migrations | every Flyway migration against an empty database |
| Duplicate, Concurrency, Worker Recovery | replay, concurrency and recovery regressions |
| Helm and Terraform Policy | chart lint/render, Terraform validate, Trivy config scan, SLO rule tests |
| Canonical Synthetic API and Browser E2E | the full sandbox stack plus the Playwright contract |
| Container Scan and SBOM | Trivy image scan and an SPDX SBOM per image |
| Backend Dependency Scan | OWASP dependency-check against cached NVD data |
| Full-History Secret Scan | gitleaks over the whole history |

A dated local test count is not release authority; use the current protected PR result.
Scheduled load, soak and restore drills are described in
`docs/operations/load-soak-restore-drills.md`.

## Admin testing note

The public registration flow creates normal `ROLE_USER` accounts. To test admin screens against
the real backend, seed or promote a user with `ROLE_ADMIN` in the account-service database before
logging in.

Playwright reads the admin login from `E2E_ADMIN_USERNAME` and `E2E_ADMIN_PASSWORD`. Set both to
match the seeded local admin account before running `npm run e2e`. Persisted Docker volumes may
contain an older password. Do not commit a real admin password.

Maven wrapper scripts must stay executable for Ubuntu CI (`account-service/mvnw`,
`transaction-service/mvnw`).
