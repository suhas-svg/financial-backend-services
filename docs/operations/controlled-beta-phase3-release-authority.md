# Controlled Beta Phase 3: Release Authority and Safe In-App Demo

## Canonical acceptance authority

`.github/workflows/release-authority.yml` is the only active GitHub Actions
workflow. Its stable aggregate check is `Controlled Beta Release Authority /
Required Acceptance`. Configure that one check as required for `main`; all
component jobs are dependencies and cannot be skipped without failing the
aggregate.

The workflow covers frontend lint, unit/component tests, the sandbox production
build, WCAG A/AA checks, Java 21/22 complete Maven test suites for both services, fresh
PostgreSQL migrations, duplicate/concurrency and worker-recovery regressions,
the fresh seven-service synthetic API/browser contract, Helm lint/template,
Terraform format/validate and configuration policy scanning, image
vulnerability scans, SPDX SBOMs, and full-history secret scanning.

The old `e2e-tests` Jest harness is quarantined and fail-closed. It is not
release evidence. `docker-compose-e2e.yml` and `docker-compose-full-e2e.yml` are
also legacy artifacts and are forbidden from the canonical workflow. A rebuild
must replace the canonical authority rather than establish a second one.

## Evidence and claims

Automated Playwright evidence is the repeatable API/browser contract in
`frontend/tests/sandbox-e2e`. It may accept the disposable CI self-signed
certificate in its isolated browser context. That is not the manual Codex
in-app demonstration and must not be described as one.

The manual demonstration uses a short-lived localhost-only CA trusted by the
Windows CurrentUser store. It does not activate a provider, real payment rail,
legal approval, licensing, disaster recovery, or production readiness.
`financial-mcp-server` and real external providers remain outside runtime scope.

## Reversible localhost trust lifecycle

Generate ignored artifacts without changing trust:

```powershell
.\scripts\manage-synthetic-sandbox-tls.ps1 -Action Create
.\scripts\manage-synthetic-sandbox-tls.ps1 -Action Status
$env:SANDBOX_TLS_DIR = (Resolve-Path .\.sandbox\tls).Path
```

Start the exact checkout using both Compose files and fresh volumes. Supply all
runtime secrets through ignored local inputs or the current process:

```powershell
docker compose --project-name financial-synthetic-sandbox-phase3 `
  -f docker-compose.synthetic-sandbox.yml `
  -f docker-compose.synthetic-sandbox.trusted-tls.yml `
  up --build --detach --wait
```

`Create` produces a three-day CA and a two-day leaf valid only for `localhost`,
`127.0.0.1`, and `::1`. The CA signing key is destroyed immediately. The leaf
private key and manifest stay under ignored `.sandbox/tls`.

Installing trust is a separate user authorization boundary. Only after the user
explicitly authorizes this exact mutation, run:

```powershell
.\scripts\manage-synthetic-sandbox-tls.ps1 -Action InstallTrust `
  -Authorization "AUTHORIZE LOCALHOST TRUST"
.\scripts\manage-synthetic-sandbox-tls.ps1 -Action VerifyTrustedEndpoint
```

Do not click through or bypass a browser certificate interstitial. If trusted
verification fails, stop and repair the lifecycle.

After the in-app walkthrough, remove and verify trust before deleting artifacts:

```powershell
.\scripts\manage-synthetic-sandbox-tls.ps1 -Action RemoveTrust
.\scripts\manage-synthetic-sandbox-tls.ps1 -Action Status
.\scripts\manage-synthetic-sandbox-tls.ps1 -Action Destroy
```

The bounded manual record should identify the exact commit/image tag, Compose
project, health state, browser used, visible synthetic classification, operator
bootstrap result, MFA-backed seed result, seeded account IDs and exact balances,
zero-account closure final state, backend comparison, cleanup verification, and
any untested boundary. Never store credentials, TOTP secrets, recovery codes,
JWTs, private keys, raw browser state, or raw transcripts.
