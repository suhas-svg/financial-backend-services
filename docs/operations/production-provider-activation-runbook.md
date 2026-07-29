# Production Provider Activation & Compliance Enablement

This runbook operates the provider-neutral control plane. It is technical
evidence, not vendor, security, legal, regulatory, jurisdiction, or production
approval. It does not initiate transfers and cannot make forecast FX executable.

## Inputs that must come from external owners

Do not create an `APPROVED` record until the relevant owners supply references
to all applicable evidence:

| Owner | Required input |
| --- | --- |
| Vendor management | Named provider, executed contract or sandbox entitlement, contract identifier, receipt/decision/quote semantics, SLA, reconciliation feed, incident owner, data residency, retention, and usage rights |
| Security/IAM | IdP issuer, audiences, JWKS/signing-key lifecycle, stable subject, allowlisted claims, access-review and revocation feeds, break glass and segregation of duties |
| Security/KMS | Secret-manager reference, versioned key identifier, rotation schedule, audit export, availability objective, recovery procedure, and completed re-encryption exercise |
| Risk governance | Authenticated decision schema, model and policy version, consistency evidence, timeout/malformed/unavailable behavior, manual-review ownership, and any applicable adverse-action review |
| FX/legal | Licensed market-data entitlement, authentication reference, quote provenance, market calendar, staleness rule, reconciliation, usage rights, jurisdiction decision, and approved disclosure |
| Legal/compliance | Approved consent/disclosure version and hash, product classification, jurisdiction eligibility, accessibility review, retention schedule, withdrawal and complaint handling, and customer/operator wording |

Before enabling any production activation, independently pass the global
real-money evidence gate in `real-money-production-readiness-gate.md`. It covers
restore drills, external incident exercises, sustained load, and change
governance in addition to the provider-specific controls below.
Never paste credentials, signing secrets, private keys, tokens, or raw customer
data into an API request, chat, evidence record, or repository. Store only an
external reference such as `vault://...`, `secret://...`, or `kms://...` and a
versioned key identifier.

## Lifecycle and separation of duties

1. A configuring operator creates `DRAFT` using an `Idempotency-Key` and recent
   `X-Operator-Mfa-Evidence` plus `X-Operator-Mfa-Verified-At`.
2. A different operator runs the boundary certification harness. Every required
   check and the health/SLA, rollback, and disaster-recovery references must be
   recorded before the state becomes `SANDBOX_CERTIFIED`.
3. A third operator records the security review and actual external approval
   references. FX, consent, and risk also require legal/compliance and
   jurisdiction review references before the state becomes `APPROVED`.
4. A fourth operator may activate only in the `production` Spring profile when
   `INTEGRATION_ACTIVATION_PRODUCTION_ENABLED=true`. Local, demo, placeholder,
   test, or missing references fail closed. Credential references must use an
   approved external secret URI.
5. An administrator with recent MFA may emergency-suspend a certified,
   approved, or active boundary. Suspension blocks future provider use; it
   never reverses completed ledger history.

Every transition writes an immutable event with actor, status change, request
fingerprint, MFA evidence reference, external evidence reference, and timestamp.
Retries with the same actor and idempotency key return the original activation;
different payloads are rejected.

## Certification harness matrix

| Boundary | Required checks |
| --- | --- |
| Notification | `DELIVERY`, `RECONCILIATION`, `FAILURE_BEHAVIOR`, `WEBHOOK_REPLAY` |
| IdP | `CLAIM_MAPPING`, `REVOCATION`, `UNKNOWN_CLAIM_DENIAL`, `ACCESS_REVIEW` |
| KMS | `ROTATION`, `REENCRYPTION`, `KEY_VERSION_MISMATCH`, `RECOVERY` |
| Risk | `DECISION_CONSISTENCY`, `TIMEOUT_FAIL_CLOSED`, `MALFORMED_FAIL_CLOSED`, `POLICY_PROVENANCE` |
| FX | `FORECAST_ONLY`, `PROVENANCE`, `STALENESS`, `RECONCILIATION` |
| Consent | `VERSION_HASH`, `JURISDICTION`, `WITHDRAWAL`, `EVIDENCE_EXPORT` |

The harness records sanitized pass/fail evidence; it does not certify a vendor
or manufacture an external approval. A failed run remains immutable and leaves
the activation in `DRAFT`.

## API

- `GET /api/admin/provider-activations`
- `GET /api/admin/provider-activations/{activationId}`
- `POST /api/admin/provider-activations`
- `POST /api/admin/provider-activations/{activationId}/certify`
- `POST /api/admin/provider-activations/{activationId}/approve`
- `POST /api/admin/provider-activations/{activationId}/activate`
- `POST /api/admin/provider-activations/{activationId}/suspend`
- `GET /api/outcome-protection/provider-activation-status`
- `POST /api/provider-activations/{activationId}/webhooks`

The webhook hook validates timestamp freshness, delegates cryptographic
verification to the named provider adapter, then persists a unique delivery ID
and payload digest. The bundled verifier is intentionally unconfigured and
rejects all callbacks. A provider-specific verifier must be implemented and
security-reviewed after a vendor is selected.

## Deployment and rollback

1. Apply transaction-service Flyway migration `V27`.
2. Deploy with `docker-compose.provider-activation.yml` or the equivalent Helm
   value. Keep production activation disabled.
3. Verify all existing provider boundaries remain fail closed.
4. Create and certify sandbox records using different test operators.
5. Exercise duplicate idempotency keys, concurrent certification, stale MFA,
   stale webhook timestamps, duplicate webhook delivery IDs, emergency
   suspension, database restore, and rollback.
6. Export activation detail and immutable evidence to the external change
   record.
7. Enable the deployment flag only after vendor, security, risk, legal,
   compliance, and jurisdiction owners have supplied their real evidence.
8. On incident, suspend the boundary first, preserve evidence, keep the Balance
   Shield kill switch disabled, and follow the provider incident and recovery
   procedures. Never infer successful delivery, approval, or settlement from a
   transport response.

## Still blocked until supplied externally

Named providers, executed contracts, sandbox credentials, production secret
references, licensing/entitlements, provider-specific webhook verification,
security review, SLAs, legal/compliance approval, jurisdiction decisions,
approved consent/disclosure wording, and production change approval.
