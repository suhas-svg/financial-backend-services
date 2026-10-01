# Changelog

All notable changes are recorded here by [release-please](https://github.com/googleapis/release-please)
from [Conventional Commits](https://www.conventionalcommits.org/) on `main`. Versions follow
[Semantic Versioning](https://semver.org/); while the product is in controlled beta (0.x),
a `feat` bumps the minor version and a `fix` the patch version.

## [0.2.0](https://github.com/suhas-svg/financial-backend-services/compare/v0.1.0...v0.2.0) (2026-10-01)


### Features

* **api:** OpenAPI specs, RFC 9457 Problem Details everywhere, generated frontend types ([#87](https://github.com/suhas-svg/financial-backend-services/issues/87)) ([ac46dca](https://github.com/suhas-svg/financial-backend-services/commit/ac46dca7b38be018f3e2a1af11996ddab0a79706))
* **ops:** scheduled load, soak and backup-restore drills with readiness-gate evidence ([#89](https://github.com/suhas-svg/financial-backend-services/issues/89)) ([0e0295f](https://github.com/suhas-svg/financial-backend-services/commit/0e0295f883d3b5e2249ade0dbb912f1312372ab0))
* **release:** one semantic version, release-please changelog, commit-pinned release images ([#88](https://github.com/suhas-svg/financial-backend-services/issues/88)) ([8c65342](https://github.com/suhas-svg/financial-backend-services/commit/8c653421d78f8cfdb476ab174548d6413d8911ea))
* **security:** password change that signs the user out everywhere ([#94](https://github.com/suhas-svg/financial-backend-services/issues/94)) ([3d39c3c](https://github.com/suhas-svg/financial-backend-services/commit/3d39c3c2c7d4941cd8f70b6ab0b2b2a776b67c53))
* **security:** RS256 tokens with published JWKS; no secret shared between services ([#93](https://github.com/suhas-svg/financial-backend-services/issues/93)) ([eb46521](https://github.com/suhas-svg/financial-backend-services/commit/eb465218d6555556db15b7169dbb6e27da52624e))


### Bug Fixes

* **ops:** restore drill compared a parsed timestamp against its text form ([#91](https://github.com/suhas-svg/financial-backend-services/issues/91)) ([5db94d6](https://github.com/suhas-svg/financial-backend-services/commit/5db94d6a2a5c832e4bc08b1a690f02f07e4c0259))
* **transfers:** stop the connection pool deadlocking under transfer load ([#92](https://github.com/suhas-svg/financial-backend-services/issues/92)) ([65e2029](https://github.com/suhas-svg/financial-backend-services/commit/65e202926677fd1bb958a7651e3d8ea975c59527))

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
