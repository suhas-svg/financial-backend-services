# Balance Shield production readiness

## Scope and non-goals

This increment makes the existing Outcome Protection forecast safer to operate in production. It adds reliable warning delivery, deployment-controlled ledger bootstrap, source-local schedule semantics, and forecast-only FX normalization.

It does not authorize or execute transfers, trade currency, guarantee an outcome, replace treasury/risk controls, or activate Balance Shield guardrails. Customer-fund movement remains exclusively in the existing authorized transfer and scheduled-transfer paths.

## Durable notification delivery

A warning event and its outcome notification delivery row are committed in the same transaction. The delivery identifier, warning event identifier, and customer-facing dedupe key are deterministic. A dispatcher claims due rows under a database lock and calls the existing account-service internal notification boundary.

States are PENDING, RETRY_SCHEDULED, DELIVERED, and TERMINAL_FAILED. Attempts use bounded exponential backoff. Exhausted attempts become terminal, while an overdue undelivered row records SLA escalation evidence. Replays reuse the same outbox row; the account service also enforces unique delivery and dedupe identifiers and records first/last receipt plus delivery count. Customer acknowledgement remains the existing owned, idempotent, audited account-service operation.

No broker is assumed. Production operations must monitor terminal failures and SLA escalation and must provision the real notification channel behind account service.

## Source-local schedule semantics

Scheduled transfers retain:

- IANA source timezone
- authoritative source-local date and time
- overlap policy (EARLIER or LATER)
- gap policy (SHIFT_FORWARD or REJECT)
- monthly anchor day and end-of-month intent

The same cadence resolver is used by execution and forecasting. This keeps weekly and monthly intent stable through DST changes, explicitly handles ambiguous/nonexistent times, preserves end-of-month cadence, and applies inclusive horizon boundaries. Legacy rows are forward-migrated as UTC.

## Forecast-only FX

FxRateProvider is an explicit read-only quote boundary. Configured quotes carry quote/base currency, decimal rate, as-of time, provider, and provenance. Conversion uses decimal arithmetic and deterministic rounding. Cross-currency forecasts fail closed when a quote or required evidence is absent or stale under REJECT; WARN retains visible stale evidence.

The API and frontend show original amounts, normalized base values, quote metadata, and executableFx=false. No FX transaction, reservation, or executable price is created.

Configuration:

    outcome.protection.fx.rates=${OUTCOME_PROTECTION_FX_RATES:}
    outcome.protection.fx.as-of=${OUTCOME_PROTECTION_FX_AS_OF:}
    outcome.protection.fx.provider=${OUTCOME_PROTECTION_FX_PROVIDER:}
    outcome.protection.fx.provenance=${OUTCOME_PROTECTION_FX_PROVENANCE:}
    outcome.protection.fx.max-age=${OUTCOME_PROTECTION_FX_MAX_AGE:PT24H}
    outcome.protection.fx.staleness-policy=${OUTCOME_PROTECTION_FX_STALENESS_POLICY:REJECT}

OUTCOME_PROTECTION_FX_RATES uses QUOTE/BASE=RATE entries separated by semicolons, for example USD/INR=83.2500;EUR/INR=90.1000. Production must replace this static configuration provider with a reviewed market-data integration without changing the provider boundary.

## Deployment bootstrap control

The bootstrap API accepts only an authenticated ROLE_ADMIN operator and requires X-Operator-Request-Id. Internal-service identity is deliberately insufficient. The read-only preflight returns operator and request correlation evidence and fails closed unless maintenance mode and every existing bootstrap invariant pass.

Use scripts/ledger-bootstrap-deployment.ps1 from the approved deployment environment. The script always performs preflight first. A write run additionally requires LEDGER_BOOTSTRAP_CONFIRM_RUN=RUN. The short-lived admin JWT is read from LEDGER_BOOTSTRAP_OPERATOR_TOKEN and is never printed. Every run stores operator, role, request identifier, origin, status, counts, currencies, and sanitized failure evidence.

Bootstrap remains idempotent and maintenance-gated. It may seed system accounts and import an existing customer balance as an opening journal only during the separately approved ledger cutover. It never creates a transfer, activates a schedule, invents customer funds, or enables ledger authority.

## External and regulatory boundaries

- Notification delivery still requires a production channel/provider, delivery webhook policy, retention policy, and incident ownership.
- FX data still requires a licensed/contracted source, entitlement controls, market-calendar policy, quote validation, and jurisdiction-specific disclosures.
- Outcome forecasts are informational. Legal/compliance review must approve wording, retention, accessibility, suitability, complaint handling, and any future automated-action consent.
- Operator authentication depends on the deployment identity provider issuing short-lived ROLE_ADMIN credentials; secret storage, rotation, break-glass, and segregation-of-duties controls remain platform responsibilities.
- Phase 2 consent-driven execution is intentionally absent and must not reuse forecast state as authorization.

## Validation contract

Focused tests cover outbox replay/concurrency/retry/terminal evidence, account-service dedupe, DST overlap/gap/month-end behavior, FX missing/stale/decimal behavior, and operator-role enforcement. Release validation also requires full account-service and transaction-service tests, frontend tests/build, fresh PostgreSQL migrations, cross-service smoke checks, and a real-browser walkthrough before publication.
