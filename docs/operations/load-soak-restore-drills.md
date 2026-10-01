# Load, soak and backup-restore drills

Scheduled CI jobs that produce the evidence the real-money readiness gate asks for
(`LOAD_SOAK` and `BACKUP_RESTORE` in
[`production-readiness-evidence.schema.json`](production-readiness-evidence.schema.json)).
They run in `release-authority.yml` against a fresh synthetic sandbox built from `main`.

| Drill | When | What passes |
| --- | --- | --- |
| Load | daily 02:17 UTC | 10 min, ramping to 10 transfer pairs/s: transfer p99 < 1 s, < 0.1% 5xx, reads p95 < 500 ms, every invariant checkpoint healthy |
| Soak | Sundays 04:17 UTC | 4 h at 3 transfer pairs/s, same thresholds, invariant checkpoint every 15 min |
| Backup/restore | daily 02:17 UTC | restored data equals the backup, ledger balances, RTO and RPO within target |

Run one on demand: **Actions → Controlled Beta Release Authority → Run workflow**, pick `drill`.

## Load and soak

[`performance/k6/transfers.js`](../../performance/k6/transfers.js) bootstraps the synthetic
operator, enrolls TOTP, seeds a funded and an empty account, then moves 0.01 USD there and
back on every iteration and reads balances and history. The thresholds are the transfer SLOs
from [observability.md](observability.md).

While k6 runs, `run-synthetic-soak.ps1` checks the money every few minutes: every journal
balances, no completed transaction lacks a journal, no idempotency key was used twice, no
scheduled run or operation claim is stuck, nothing sits in a terminal outbox state. A drill
that is fast but corrupts a balance fails.

The stack runs with [`docker-compose.synthetic-load.yml`](../../docker-compose.synthetic-load.yml),
which lifts only the rapid-transfer step-up rule and per-IP auth throttling. Both exist to
protect real customers and would otherwise answer the test instead of the transfer path.

Locally:

```bash
docker compose -f docker-compose.synthetic-sandbox.yml -f docker-compose.synthetic-load.yml up -d --build --wait
k6 run -e PROFILE=load -e RATE=10 performance/k6/transfers.js
```

(set the `SANDBOX_*` variables from `docker-compose.synthetic-sandbox.yml` first).

The four-hour CI soak is recurring evidence, not the seven-day soak in
[controlled-beta-phase4-operations.md](controlled-beta-phase4-operations.md); GitHub-hosted
jobs stop at six hours. Use the scheduled-task soak for the full seven days.

## Backup and restore

1. k6 writes two minutes of transfers, then stops, so the backup describes a known state.
2. [`backup-synthetic-sandbox.ps1`](../../scripts/backup-synthetic-sandbox.ps1) dumps both
   databases and records a snapshot in the receipt: row counts, balance totals, the newest
   committed transaction.
3. [`restore-verify-synthetic-sandbox.ps1`](../../scripts/restore-verify-synthetic-sandbox.ps1)
   restores into new, network-isolated PostgreSQL containers and fails unless:
   - every snapshot value matches the receipt (no lost or extra rows, same totals);
   - every journal in the restored ledger balances;
   - **RTO** (fresh server to verified data, both databases) is within `RtoTargetSeconds`;
   - **RPO** (restore start minus the newest committed transaction in the backup, i.e. what a
     failure at that moment would lose) is within `RpoTargetSeconds`.

Targets default to 900 s each. Set the real objectives as repository variables
`DRILL_RTO_TARGET_SECONDS` and `DRILL_RPO_TARGET_SECONDS`. The drill restores right after
backing up, so its RPO shows the mechanics work; in production the RPO is bounded by how often
backups (or WAL archiving) run, which must be at least as frequent as the target.

## Evidence

Each run uploads `evidence-<drill>-<run id>` (kept 90 days) containing a `.tar.gz` of the raw
evidence (k6 summary, invariant checkpoints, backup receipt, restore result) and
`evidence-manifest.json`. The job summary shows the two values the readiness manifest needs:

| Manifest field | Value |
| --- | --- |
| `reference` | the workflow run URL |
| `evidenceSha256` | SHA-256 of the archive |

A reviewer downloads the artifact, checks the archive hash, reads the results, and records
the gate in the external manifest as usual.
