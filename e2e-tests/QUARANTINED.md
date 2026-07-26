# Quarantined legacy E2E harness

This directory is retained temporarily for historical comparison only. Its
service ports, credentials, orchestration, and financial assumptions predate
the controlled-beta synthetic sandbox and are not an acceptance authority.

All executable package scripts fail closed through `scripts/quarantined.js`.
Do not use output from this directory as release evidence. The canonical API
and browser contract is `frontend/tests/sandbox-e2e/controlled-beta-phase2.spec.ts`,
run only by `.github/workflows/release-authority.yml` against a fresh isolated
`docker-compose.synthetic-sandbox.yml` project.

Reactivation requires a product decision and a rebuild against current API,
ledger, identity, TLS, topology, and synthetic-data contracts. It must replace,
not supplement, the canonical authority.
