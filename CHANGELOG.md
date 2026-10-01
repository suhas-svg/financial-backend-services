# Changelog

All notable changes are recorded here by [release-please](https://github.com/googleapis/release-please)
from [Conventional Commits](https://www.conventionalcommits.org/) on `main`. Versions follow
[Semantic Versioning](https://semver.org/); while the product is in controlled beta (0.x),
a `feat` bumps the minor version and a `fix` the patch version.

## 0.1.0 (2026-10-01)

First versioned baseline. Everything on `main` up to #85, including:

### Features

* Customer and admin consoles: accounts, beneficiaries, transfers, scheduled and recurring
  transfers, statements, disputes, notifications, MFA and step-up authorization.
* Double-entry ledger with projections, reconciliation and statement generation.
* Outcome Protection and Balance Shield guardrails.
* Risk alerts, risk cases and investigations for operators.

### Security

* Short-lived access tokens with rotating httpOnly refresh sessions (#84).
* Login and registration throttling shared across replicas; corrected ingress rate limits (#83).
* Strict Content-Security-Policy and cross-origin isolation headers (#83, #86).

### Observability

* OpenTelemetry tracing across both services and their SQL; transfer success and p99 SLOs
  with multi-window burn-rate alerts (#85).

### Performance

* Indexed ledger lookups, collapsed notification summary queries, lighter polling (#82).
* Charts library loaded only on pages that draw charts (#86).
