# Deployment authority

The repository supports one controlled synthetic-beta runtime:
`docker-compose.synthetic-sandbox.yml`, operated through the scripts and runbook
under `scripts/` and `docs/operations/controlled-beta-phase2-sandbox.md`.

The root production-like Compose file, startup script, and parallel
`account-service/k8s` manifests were removed because they were outside the
canonical acceptance workflow and contradicted the fail-closed production
boundary.

Helm charts under `infrastructure/helm` are the only Kubernetes source of truth.
They are linted, rendered, and policy-scanned by the controlled-beta release
authority. They are technical deployment templates, not authorization for a
real-data or real-money environment.

The controlled synthetic sandbox:

- uses synthetic data only;
- publishes only the loopback TLS gateway;
- requires explicit non-default secrets and a pinned image tag;
- never activates the `prod` or `production` Spring profiles;
- does not activate providers, payment rails, or real credentials.

Production requires both the external evidence gate in
`docs/operations/real-money-production-readiness-gate.md` and the provider
activation approvals in
`docs/operations/production-provider-activation-runbook.md`. No convenience
Compose file or repository script may claim to make the platform production
ready.
