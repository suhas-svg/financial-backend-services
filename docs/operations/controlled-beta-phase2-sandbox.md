# Controlled Beta Phase 2: Reproducible Synthetic Sandbox

This topology is for synthetic data only. It does not activate providers, prove
licensing or legal readiness, provide disaster recovery, or constitute soak
evidence. `financial-mcp-server` is excluded.

## Security boundary

- The only published socket is loopback HTTPS at `https://localhost:8443`.
  When `SANDBOX_HTTPS_PORT` overrides the default, the gateway renders that
  exact numeric port into its localhost/127.0.0.1 browser-origin allowlist and
  rejects loopback origins using any other port.
- Account service, transaction service, both PostgreSQL databases, Redis, and
  the static frontend are reachable only on private Compose networks.
- The gateway terminates TLS, serves the production-built frontend through an
  internal static server, applies CSP/frame/content-type/referrer/permissions
  headers, and rejects browser API origins outside localhost/127.0.0.1.
- Every page and both metadata APIs classify the runtime as
  `SYNTHETIC_SANDBOX`, `realMoney=false`.
- Synthetic controls fail at startup unless the active Spring profile is
  exactly `synthetic-sandbox`; production/prod profiles never enable them.

## Required runtime inputs

Set unique values in the current shell or an ignored local secret source. Never
store them in this repository:

`SANDBOX_IMAGE_TAG`, `ACCOUNT_DB_PASSWORD`, `TRANSACTION_DB_PASSWORD`,
`REDIS_PASSWORD`, `JWT_SECRET`, `INTERNAL_JWT_SECRET`, `MFA_ENCRYPTION_KEY`, and
`SANDBOX_BOOTSTRAP_TOKEN`.

JWT/HMAC values and the MFA encryption key must be at least 32 random bytes.
There are no shared, demo, or default login credentials.

Run configuration validation and start the exact checkout:

```powershell
.\scripts\validate-synthetic-sandbox.ps1
docker compose --project-name financial-synthetic-sandbox -f docker-compose.synthetic-sandbox.yml up --build --detach --wait
```

The default gateway creates a 30-day local self-signed certificate in a named
volume. For a locally trusted certificate, create `tls.crt` and `tls.key` for
`localhost` with the workstation-approved local CA tool, set
`SANDBOX_TLS_DIR`, and include
`docker-compose.synthetic-sandbox.trusted-tls.yml`. Certificate private keys
remain outside Git.

## First operator and MFA

1. Confirm `GET /account-api/api/sandbox/bootstrap/status` reports
   `setupRequired=true`.
2. POST a unique username and a password of at least 14 characters to
   `/account-api/api/sandbox/bootstrap`, sending the runtime-only value in
   `X-Sandbox-Bootstrap-Token`.
3. A second bootstrap is rejected. Log in at `/login?portal=admin`.
4. Enroll and confirm TOTP at `/security`. No seed/reset operator action is
   accepted without an active MFA method and a recent action-bound proof.

## Idempotent seed flow

Use one stable `Idempotency-Key` for the complete operation:

1. POST `/transaction-api/api/sandbox/seed/challenge` as the operator.
2. Verify its challenge at
   `/account-api/api/security/challenges/{challengeId}/verify` with TOTP or a
   single-use recovery code.
3. POST the returned proof and challenge ID to
   `/transaction-api/api/sandbox/seed` with the same idempotency key.

The account service creates one zero account and one funded account using a
versioned seed registry. The transaction service funds only the latter through
the Phase 1 synthetic-funding service, producing the normal balanced journal
and projection. Replays return the same account identities and funding
idempotency identity; changing the key changes the action fingerprint and
invalidates the proof.

## Reset/reseed

Reset destroys only volumes owned by the fixed
`financial-synthetic-sandbox` Compose project. It requires both an exact
profile environment value and explicit confirmation:

```powershell
$env:SANDBOX_PROFILE = "synthetic-sandbox"
.\scripts\reset-reseed-synthetic-sandbox.ps1 -Confirmation "RESET SYNTHETIC SANDBOX"
```

After reset, repeat first-operator bootstrap, MFA enrollment, and seed. The
script cannot target another Compose file or production profile.

## Verification checklist

- Gateway: TLS 1.2/1.3, security headers, synthetic classification, static UI.
- Exposure: only `127.0.0.1:8443`; direct 8080/8081/5432/6379 connections fail.
- Bootstrap: first succeeds, replay fails, bad/missing token fails, no defaults.
- Seed: zero/funded IDs stable; funding replay stable; journal debits equal credits.
- Lifecycle: close the zero account and verify retained history; funded closure
  remains blocked until balanced back to zero through authorized ledger flows.
- Recovery: run the existing bounded scheduled-transfer, notification, and
  projection recovery tests and verify no duplicate money movement.
