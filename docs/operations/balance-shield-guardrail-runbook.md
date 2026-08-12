# Balance Shield executable guardrail runbook

## Safety model

Balance Shield execution is fail-closed. Migration `V25` creates the `GLOBAL` runtime control with execution disabled. Activation never moves money, and there is no scheduler or background worker that initiates guardrail actions. Every movement starts with an authenticated customer confirmation and uses the existing authorized transfer flow, including ownership, balance, risk, MFA, idempotency, and double-entry ledger controls.

Only same-currency transfers between two accounts owned by the customer are supported. The funding account must be outside the protected scenario scope. Forecast FX remains explanatory only and is never used to execute currency conversion.

## Deployment

1. Deploy transaction-service with Flyway enabled and confirm migration `V25` succeeded.
2. Set `OUTCOME_GUARDRAIL_TERMS_VERSION` to the legally approved terms identifier. Changing it suspends effective execution for older policies until new informed consent is implemented and recorded.
3. Confirm `GET /api/admin/outcome-protection/guardrails/control` returns `executionEnabled: false` after a fresh deployment.
4. Confirm the production identity provider maps only the approved operations role to `ROLE_ADMIN`. Customer and internal-service identities must receive `403` on every `/api/admin/outcome-protection/guardrails/**` route.
5. Review policies and immutable control evidence before enabling execution.

## Enable an approved execution window

Use a unique, operator-generated idempotency key and an authenticated `ROLE_ADMIN` token:

```http
PUT /api/admin/outcome-protection/guardrails/control
Idempotency-Key: <change-ticket>-enable-1
Authorization: Bearer <operator-token>
Content-Type: application/json

{"executionEnabled":true,"reason":"Approved change <ticket>; monitoring staffed until <UTC timestamp>"}
```

Read the control and `/control/events` back. Verify `changedBy`, reason, timestamp, and state match the approved operator and ticket. Replaying the same key and payload is safe; using that key for different input fails.

## Emergency kill switch

```http
PUT /api/admin/outcome-protection/guardrails/control
Idempotency-Key: <incident>-kill-1
Authorization: Bearer <operator-token>
Content-Type: application/json

{"executionEnabled":false,"reason":"Incident <id>: guardrail execution suspended"}
```

The kill switch blocks new execution and pending MFA completion. It does not reverse completed transfers. Review customer-visible policy state, pending authorization records, transfer/ledger evidence, and notification outbox delivery. Cancel or resolve pending authorizations through their existing authorized controls; never create a compensating transfer directly from this runbook.

## Customer evidence sequence

The expected evidence chain is draft preview, versioned informed consent, action-bound activation MFA, active policy, explicit action confirmation, optional risk MFA, authorized transfer result, immutable domain event, and account-service notification evidence. Suspension, revocation, expiry, stale terms, absent runtime control, or a disabled kill switch must prevent execution.

## Authoritative source divergence

`SCENARIO_DIVERGED` is a financial safety rejection, not a retryable transfer error. Confirm the immutable event contains saved/current source fingerprints, `outcome-source-v2`, scenario/version/result/guardrail identifiers, stage, redacted changed-field names, and `moneyMoved=false`. Never copy MFA proofs, tokens, credentials, raw requests, or unredacted account/schedule values into incident notes.

Tell the customer: **Authoritative state changed. Refresh or re-run the scenario, then select and consent to a fresh repair.** Do not reactivate the old repair or bypass the fingerprint comparison. For a rejection at `PRE_AUTHORIZATION_COMPLETION`, verify the pending transfer authorization was cancelled and the policy reservation released. If cancellation failed, keep the reservation in place and retry the existing cancellation/recovery path; do not create a compensating transfer or edit balances directly.

The canonical comparison runs immediately before transfer submission and MFA-authorized completion. Transaction-service cannot atomically lock account-service state across that network boundary, so retain the existing transfer authorization, ownership/status, balance, limit, hold, risk/MFA, idempotency and double-entry checks as the final money-movement authority.

## External and regulatory boundaries

- Legal/compliance owns terms wording, version approval, disclosures, consent retention period, and jurisdiction eligibility.
- The production identity provider owns operator identity proofing, role issuance, access review, and revocation.
- Account-service owns MFA enrollment/challenge verification and customer notification delivery providers.
- Transaction-service owns transfer authorization and ledger posting; Balance Shield cannot bypass or replace either.
- Licensed FX providers are forecast inputs only. Executable FX, external payment rails, autonomous sweeps, overdraft/credit, investment advice, and guaranteed-outcome claims remain out of scope.
