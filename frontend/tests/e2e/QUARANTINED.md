# Legacy frontend E2E quarantine

The former tests in this directory used the Vite HTTP development proxy, assumed
an undocumented default administrator credential, and duplicated customer flows
that are now verified against the reproducible seven-service TLS sandbox.

They are intentionally not an acceptance authority. Run `npm run e2e` to execute
the canonical `tests/sandbox-e2e` contract after starting the synthetic sandbox.
The accessibility-only production-build checks remain under `tests/accessibility`.
