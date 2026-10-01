# Production Integration & Regulatory Readiness

**Status:** implementation contract; not legal, regulatory, provider, or production approval
**Authoritative baseline:** `origin/main` at `0e1fdaac4fdda2217cbfcfef50fa1cd2e1e29169`

## Safety outcome

This increment adds explicit, replaceable integration boundaries and durable operational evidence around Balance Shield. It does not expand the set of actors or code paths that may move customer funds. Every guardrail transfer remains customer initiated, explicitly confirmed, owner scoped, idempotent, risk evaluated, MFA/step-up protected when required, and posted only through the existing authorized double-entry transfer flow. Forecast FX is never executable.

Production profiles must fail startup when a required provider, identity contract, key version, consent version, or policy mode is absent or uses a local/test adapter. Non-production profiles remain usable only when the adapter is explicitly selected.

## Threat model

| Threat | Required control |
| --- | --- |
| A provider reports success without durable acceptance | Persist a sanitized provider receipt, provider identifier, classification, attempt time, correlation identifier, and reconciliation state; never treat a transport response alone as proof of customer delivery. |
| Notification outage causes duplicate or lost messages | Preserve the transactional outbox and deterministic dedupe key; classify retryable timeout/unavailable responses separately from terminal rejection; reconcile by delivery and provider receipt identifiers. |
| A local adapter is deployed as production | Production startup rejects local, test, unconfigured, or fail-open provider modes and missing credentials/contract identifiers. |
| Forged or over-privileged operator token | Validate signature, issuer, audience, subject, expiration, and an allowlisted claim-to-role mapping. Unknown claims grant no role. Internal-service identity never becomes an operator. |
| Revoked operator retains access | Record external identity subject, issuer, access-review reference, reviewed-at, and revocation evidence; expired review or revoked subject fails authorization. |
| MFA secrets leak through source, logs, or rotation | Secret material exists only at the secret-manager/KMS boundary; persistence stores ciphertext plus a versioned key identifier. Logs and evidence contain identifiers and outcomes only. |
| Rotation corrupts or mixes key versions | Re-encryption locks each MFA row, decrypts with its recorded version, encrypts with the configured active version, persists an idempotent rotation run, and can be reconciled by counts without exposing plaintext. |
| Fraud provider is unavailable or manipulated | Guardrail risk policy fails closed for provider timeout, unavailable, malformed, stale, or indeterminate decisions. An external decision can require step-up or deny but can never bypass the existing transfer authorization policy. |
| Risk policy changes without governance | Version policy configuration and persist provider/policy provenance, decision reason, as-of time, and review reference in guardrail evidence. |
| FX quote is stale, missing, unlicensed, or unauthenticated | Require provider contract identifier, authentication reference, quote identifier, provenance, as-of/received-at times, maximum age, and reconciliation state. Missing/stale/invalid data fails the forecast closed. |
| Forecast quote is reused to execute FX | Keep `executableFx=false`; no provider interface exposes order, reservation, settlement, or payment methods. |
| Unapproved consent text becomes effective | Only an approved, effective, jurisdiction-eligible version may be offered. Persist exact version/hash and approval metadata; code never labels a version legally approved by itself. |
| Consent withdrawal or complaint is hidden | Persist immutable withdrawal/complaint evidence and expose owned customer evidence plus least-privilege operator evidence. Withdrawal blocks future guardrail action but never reverses a completed transfer. |
| Concurrent requests bypass limits or duplicate evidence | Retain database uniqueness, owner-scoped idempotency fingerprints, pessimistic locks, immutable events, and existing guardrail/transfer concurrency controls. |

## Failure model

- Notification `TIMEOUT`, `UNAVAILABLE`, and provider `RATE_LIMITED` outcomes are retryable within the existing bounded outbox policy. `REJECTED`, invalid destination, and invalid contract are terminal. Unknown results fail closed and remain reconcilable.
- Missing production provider configuration stops startup. Runtime provider health degradation is visible and blocks the affected integration; it never changes ledger, consent, or authorization state.
- Invalid issuer/audience, missing subject, unmapped role, stale access review, or revoked identity yields no operator authority. Configuration that would trust arbitrary roles stops production startup.
- Missing KMS key version, inaccessible key, ciphertext/key-version mismatch, or partial rotation fails closed. A rotation retry resumes idempotently and never logs plaintext.
- Risk timeout, invalid response, stale model/policy version, or unavailable provider denies the guardrail attempt before the existing authorized transfer path. `STEP_UP` can add verification; it cannot remove existing verification.
- Missing, stale, unauthenticated, unreconciled, or provenance-free FX quotes reject cross-currency forecasting. Same-currency identity quotes remain forecast-only.
- Missing approved consent version, ineligible jurisdiction, withdrawn consent, inaccessible disclosure, or retention-policy mismatch prevents new consent/action. Complaint submission records evidence but does not adjudicate it.
- Provider failures never silently fall back in a production profile. Explicit local adapters are limited to non-production.

## Non-goals

- No autonomous or scheduled Balance Shield money movement.
- No new transfer, debit, ledger, compensation, hold, or authorization implementation.
- No executable FX, rate lock, order, conversion, settlement, payment initiation, overdraft, credit, investment, or suitability decision.
- No bundled production provider credentials, contracts, endpoints, legal wording, jurisdiction rules, retention periods, or regulatory approvals.
- No claim that a provider receipt proves human receipt, that a risk response satisfies a regulatory obligation, or that consent metadata is legally sufficient.
- No role inferred from arbitrary token text and no internal-service-to-operator privilege escalation.

## External dependencies

- Contracted notification provider with credentials, endpoint, timeout/SLA, webhook authenticity, receipt semantics, reconciliation feed, data residency, retention, and incident ownership.
- Production IdP with signing-key/JWKS lifecycle, exact issuer/audience, stable subject, allowlisted group/role claims, access-review feed, revocation feed, break-glass process, and segregation of duties.
- Secret manager/KMS with authenticated encrypt/decrypt or data-key operations, versioned key identifiers, rotation schedule, availability SLO, audit export, and recovery procedure.
- Contracted fraud/risk provider with authenticated API, decision schema, policy/model version, latency/SLO, retry rules, manual-review ownership, and adverse-action/legal review where applicable.
- Licensed FX market-data provider with entitlement, authentication, quote provenance, market-calendar/staleness policy, reconciliation feed, data usage rights, and jurisdiction-approved disclosure.
- Legal/compliance-approved consent versions, jurisdiction eligibility rules, accessibility evidence, retention schedule, withdrawal handling, complaint workflow, and customer/operator disclosure wording.

## Regulatory responsibility boundary

The repository supplies technical enforcement hooks, immutable identifiers, evidence export, fail-closed configuration, and operator/customer visibility. It does not approve wording, determine product classification, establish lawful basis, decide jurisdiction eligibility, satisfy record-retention duties by itself, adjudicate complaints, certify accessibility, license market data, approve a fraud model, or attest that an external provider meets regulatory obligations.

Legal/compliance owns approved consent/disclosure versions, jurisdiction decisions, retention and accessibility requirements, complaint/withdrawal policy, and regulatory interpretation. Security/IAM owns identity proofing, role approval, access reviews, revocation, break-glass, and KMS policy. Vendor/risk management owns provider due diligence, contracts, entitlements, SLAs, and incident responsibilities. Operations owns production configuration, reconciliation, monitoring, and evidence review. Engineering owns enforcement of the documented boundaries and must not describe an unconfigured adapter as production ready.
