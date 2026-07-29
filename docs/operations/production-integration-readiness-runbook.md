# Production integration readiness runbook

This runbook verifies technical readiness only. It is not a legal, regulatory, vendor, security, or production approval.

## Release gate

1. Deploy migrations `account-service V11-V12` and `transaction-service V26` to a fresh PostgreSQL validation environment.
2. Configure production only through the secret-managed Helm deployment described in `docs/deployment-authority.md`. Never place bearer tokens, KMS material, customer data, or raw provider responses in Git.
3. Confirm both services stop during startup when any required production integration property is missing, a local adapter is selected, or provider health is unavailable.
4. Confirm the IdP token has the exact issuer and service audience, a stable subject, an allowlisted `operator_roles` value, a recent `access_reviewed_at`, and `revoked=false`. Unknown claims must not grant `ROLE_ADMIN`.
5. Read account-service `GET /api/integration-readiness` and transaction-service `GET /api/admin/outcome-protection/integration-readiness` with a short-lived operator token. Preserve the response with the change record.
6. Send a non-financial test notification. Reconcile `delivery_id`, provider receipt, classification, attempt count, and reconciliation status. Exercise timeout, rate-limit, unavailable, rejection, bounded retry, and terminal states.
7. Read MFA KMS health, rotate a non-production test enrollment with a unique `X-Operator-Request-Id`, retry that request, and reconcile examined/rotated/failed counts. Verify logs and evidence contain key identifiers only.
8. Exercise fraud/risk `ALLOW`, `STEP_UP`, `DENY`, timeout, malformed, and unavailable responses. `ALLOW` must still traverse the existing transfer risk/MFA/authorization flow; unavailable and indeterminate decisions must block before money movement.
9. Exercise FX fresh, stale, missing, unauthenticated, invalid-provenance, and reconciliation-failure cases. Confirm `executableFx=false` and that no order, rate lock, conversion, settlement, or payment endpoint exists.
10. Verify the configured consent version is effective and eligible only for approved jurisdictions. Test evidence export, withdrawal, complaint recording, repeat idempotency, foreign-customer denial, and withdrawal blocking future action.
11. Keep the global Balance Shield execution control disabled until provider, IAM, KMS, risk, FX, consent, legal/compliance, jurisdiction, retention, accessibility, complaint, monitoring, and incident owners sign the external change record.

## Incident response

- Activate the existing guardrail kill switch for suspected execution risk. It blocks new customer actions and never reverses a completed transfer.
- Disable or revoke the affected IdP subject and verify revocation evidence before restoring operator access.
- Mark provider receipts unreconciled; do not infer customer delivery from HTTP acceptance.
- Stop key rotation when any version is unavailable. Preserve the run evidence and restore KMS access; never log or export plaintext MFA material.
- Reject forecasts when FX evidence is missing or stale. Forecast data is never an executable price.
- Preserve consent withdrawal and complaint evidence. Engineering records evidence but does not adjudicate complaints or determine legal sufficiency.

## External sign-offs still required

- Notification provider contract, credentials, receipt/webhook semantics, data residency, retention, SLA, reconciliation feed, and incident ownership.
- IdP/JWKS lifecycle, identity proofing, role approval, access review, revocation, break glass, and segregation of duties.
- KMS contract, key policy, rotation schedule, audit export, recovery, and availability objective.
- Fraud/risk contract, authenticated decision schema, model/policy governance, manual review, latency objective, and applicable adverse-action review.
- Licensed FX entitlement, authentication, provenance, market-calendar/staleness rules, usage rights, disclosures, and reconciliation.
- Legal/compliance-approved consent language/version, jurisdiction eligibility, product classification, retention, accessibility, withdrawal, complaints, and customer/operator wording.
