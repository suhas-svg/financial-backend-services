# Risk-based step-up authorization

This feature pauses risky immediate transfers until the customer verifies an authenticator or one-time recovery code. No transaction or ledger journal is created before verification.

## Enable locally

Set a strong, private encryption key and enable the policy before starting the stack:

```powershell
$env:MFA_ENCRYPTION_KEY = "replace-with-a-random-secret-of-at-least-32-characters"
$env:STEP_UP_ENABLED = "true"
docker compose -f docker-compose.codex.yml -f docker-compose.codex.override.yml up --build -d
```

`MFA_ENCRYPTION_KEY` encrypts authenticator secrets at rest. Rotating it requires a planned re-enrollment or key migration. Never commit its value. The policy defaults to disabled so a deployment can apply migrations and enroll users before enforcement is activated.

## Default policy

A transfer requires step-up authorization when any of these conditions applies:

- amount is at least 5,000;
- an external destination was entered manually instead of selected from saved recipients;
- the selected recipient was created within the last 24 hours;
- it would be the fifth completed transfer in 10 minutes; or
- the source account status changed to active within the last 24 hours.

Transfers between accounts owned by the same user are not treated as manual external transfers. Other policy signals can still require verification.

Policy thresholds can be changed with `STEP_UP_HIGH_VALUE_THRESHOLD`, `STEP_UP_BENEFICIARY_COOLING_HOURS`, `STEP_UP_RAPID_TRANSFER_WINDOW_MINUTES`, `STEP_UP_RAPID_TRANSFER_COUNT`, and `STEP_UP_RECENT_UNFREEZE_HOURS`.

## Customer flow

1. Open **Security** and enroll an authenticator app after confirming the current password.
2. Save the displayed recovery codes offline. Each is single-use.
3. Submit a transfer normally. Low-risk transfers execute immediately.
4. For a challenged transfer, enter an authenticator or recovery code in the verification panel.
5. The account service issues a short-lived, action-bound proof. The transaction service consumes it once and executes the transfer using the original idempotency key.

Challenges expire after five minutes by default. Proofs expire after two minutes and are bound to the user, exact transfer fingerprint, and authorization record.

## Live smoke test

With an enabled stack running on the default isolated test ports, execute:

```powershell
.\scripts\test-step-up-authorization.ps1
```

The script creates disposable users and accounts, enrolls TOTP, consumes one recovery code, confirms that funds do not move before authorization, completes a high-value transfer, verifies its ledger journal, and checks authorization retry idempotency.
