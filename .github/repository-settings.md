# Repository release settings

GitHub is the source of truth. Protect `main` with pull requests, conversation
resolution, code-owner review where configured, dismissal of stale approvals,
no force pushes/deletions, and branches required to be current before merge.

## Required status check

Require exactly this stable aggregate check:

`Controlled Beta Release Authority / Required Acceptance`

Do not require individual matrix jobs. The aggregate depends on every frontend,
backend, migration, financial-integrity, synthetic E2E, infrastructure,
container/SBOM, and secret-scan gate and fails if any dependency is failed,
cancelled, or skipped. `scripts/verify-release-authority.ps1` fails if another
active workflow or legacy E2E authority is introduced.

Changing the branch-protection rule is an external GitHub administration action.
It must be performed and independently verified after this branch is published;
repository documentation alone does not prove the rule is active.

## Security settings

Keep GitHub secret scanning and push protection, Dependabot alerts, and CodeQL
enabled. Runtime credentials, TLS private keys, browser state, and external
provider secrets must never be stored in Actions variables, repository files,
artifacts, or logs unless the relevant product explicitly requires an encrypted
GitHub secret reference.

Phase 3 creates no deployment authority and makes no claim about providers,
contracts, licensing, legal approval, disaster recovery, or production readiness.
