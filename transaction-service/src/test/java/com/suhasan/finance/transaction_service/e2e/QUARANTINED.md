# Retired pre-sandbox full-system tests

The former `FullSystemIntegrationE2ETest` and `TransactionWorkflowE2ETest`
were removed in Controlled Beta Phase 3. They depended on obsolete HTTP DTOs,
status contracts, mutable shared service state, and a pre-ledger topology. They
failed against the authoritative service contracts and duplicated release
authority without proving the Phase 1/2 financial controls.

Canonical cross-service API/browser evidence now runs from
`frontend/tests/sandbox-e2e/controlled-beta-phase2.spec.ts` against a fresh,
isolated seven-service synthetic Compose project. Service-owned integration,
idempotency, concurrency, migration, and worker-recovery tests remain in their
owning packages and stay part of complete Maven verification.

Do not restore these classes as a second acceptance path. Any replacement must
target current DTOs, ledger authority, synthetic profile guards, one-time
bootstrap, MFA, private ports, TLS gateway, and isolated fresh volumes, and
must replace the canonical contract through an explicit product decision.
