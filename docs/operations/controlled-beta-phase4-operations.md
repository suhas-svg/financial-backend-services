# Controlled synthetic-beta Phase 4 operations

This runbook applies only to the environment visibly labelled `SYNTHETIC ENVIRONMENT • NO REAL MONEY`. It does not authorize real providers, payment rails, customer data, autonomous repair, or real-money operation.

## Automated financial operations

`financial-operations.enabled` defaults to `false` and is enabled only in an approved synthetic profile. The daily job reconciles the prior business date and the monthly job generates statements for the closed prior month. `financial-operations.zone` is an explicit IANA zone (UTC in the canonical sandbox). Each operation first acquires a durable `(operation_type, business_date)` claim. Completed dates are immutable/idempotent; failed or expired claims can be recovered with a new attempt and preserved attempt count. Jobs read immutable posted-ledger history and never mutate a balance or move money.
Statement regeneration is source-set aware: a later posted reversal or correction is represented by a new immutable statement version, while an unchanged posted journal set replays the latest version.

Operators use the admin-only endpoints under `/api/admin/financial-operations`, with an `Idempotency-Key`, authenticated operator identity, and bounded reason. Existing MFA and role controls remain in force. Pause all automation by setting `financial-operations.enabled=false` and recreating only transaction-service. Investigate `financial_operation_runs`, reconciliation exceptions, and outbox evidence before replay. Never delete an operation row to force a replay.

## Alert routing and certification

The tracked Alertmanager config contains only the local certification receiver. It records a SHA-256 receipt, alert labels/annotations, firing/resolved status, and timestamp; it contains no credential. Run `scripts/test-alerting-config.ps1 -RequireDocker` before a release request. For an external provider, place its HTTPS webhook secret outside the repository and run `scripts/render-alertmanager-provider-config.ps1`; the renderer rejects repository-local, loopback, placeholder, and non-HTTPS references and writes only to an external approved path. Missing references fail closed. Real provider contracts, credentials, routing ownership, SLA, legal review, and certification remain external blockers.

Required signals cover reconciliation exceptions, scheduled work age/stuck processing, notification backlog/oldest age/terminal failure, ledger and financial-evidence outbox backlog/terminal failure, failed/stale financial-operation claims, and PostgreSQL/Redis loss. Every alert routes firing and resolved events. On receiver failure, Alertmanager retains/retries delivery; restore the receiver, verify a resolved receipt, and preserve the evidence volume.

## Backup and restore

1. Set `SANDBOX_PROFILE=synthetic-sandbox` and run `scripts/backup-synthetic-sandbox.ps1` with a bounded output directory.
2. Preserve the receipt and SHA-256 hashes. The bundle contains synthetic PostgreSQL custom-format dumps only; Redis is a rebuildable cache/coordination dependency, not ledger authority.
3. Run `scripts/restore-verify-synthetic-sandbox.ps1 -Confirmation "RESTORE TO ISOLATED SYNTHETIC TARGET"`. It refuses non-synthetic receipts, verifies hashes, restores into isolated containers with no network or source volume, checks tables and successful Flyway history, writes a receipt, and removes disposable targets.
4. Never point the restore verifier at shared, production, or sibling-worktree volumes.

## Failure drills and soak

Run `scripts/run-synthetic-failure-drills.ps1 -Confirmation "RUN SYNTHETIC FAILURE DRILLS"`. The bounded drill covers transaction-service restart during worker activity, transaction PostgreSQL restart, Redis loss/recovery, duplicate request and stuck-schedule regression, and receiver fail-closed configuration. Review the durable JSON receipt and ledger/reconciliation evidence after ambiguous timeouts.

`run-synthetic-soak.ps1` is restartable, takes a single-runner file lock, and appends durable NDJSON checks. Every checkpoint requires exactly seven healthy services plus zero unbalanced journals, completed transactions without journals, duplicate transaction idempotency records, stuck scheduled-transfer runs, stale or failed financial-operation claims, terminal ledger outbox rows, and terminal unreconciled notification receipts.

On Windows, use the durable scheduled-checkpoint mode instead of relying on a long-lived child process:

`manage-synthetic-soak-task.ps1 -Action Install -Confirmation "INSTALL SYNTHETIC SOAK TASK" -EnvironmentFile <external-env-file> -EvidenceDirectory <external-evidence-path> -IntervalMinutes 5`

The manager installs and immediately starts a current-user Scheduled Task for eight days. Each invocation imports the external synthetic environment file without logging values and performs exactly one bounded check. Both the environment file and evidence directory must remain outside the repository. Remove the task after reviewing the final evidence with `manage-synthetic-soak-task.ps1 -Action Remove -Confirmation "REMOVE SYNTHETIC SOAK TASK"`; removal never deletes evidence.

At five-minute intervals the gate requires at least 2,017 checks, zero failures, zero monitoring gaps longer than 630 seconds, and at least 168 wall-clock hours. A short invocation is partial evidence, and a restarted task must continue the same state before the allowed gap expires. The gate passes only when `state.json` reports `completed=true`; never infer completion from an accelerated run or elapsed time alone.
