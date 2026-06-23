# Cross-Console Reliability Repair Design

## Goal

Repair the confirmed reliability defects found during the live customer/admin console audit without changing product scope or unrelated behavior.

## Confirmed defects

1. Transaction-service calls to account-service default to five seconds. Account operations can exceed this under Docker load, causing valid transactions to fail as service-unavailable errors.
2. Risk evaluation runs in `REQUIRES_NEW` transactions before the outer transaction commits. Repository counts cannot see the current transaction, so threshold alerts are generated one event late.
3. `transaction.processing.duration` is registered both without tags and with `method`, `type`, and `status` tags. Prometheus rejects the inconsistent meter identity.
4. Admin Playwright coverage still assumes the obsolete combined customer/admin shell.
5. Playwright's ten-second assertion window is shorter than observed account-service response time under local Docker load.

Frozen-account debit handling is excluded: fresh reproduction returned HTTP 400 with the correct domain message.

## Design

### Inter-service timeout

Use a 30-second account-service timeout consistently for the transaction-service HTTP client and resilience time limiter. Expose it through `ACCOUNT_SERVICE_TIMEOUT` in the live Compose configuration while retaining environment override support.

The timeout remains bounded; this change accommodates measured local latency without introducing unlimited waits or retries that could duplicate financial operations.

### Risk evaluation transaction boundary

Risk evaluation will join the caller's transaction instead of opening `REQUIRES_NEW`. The transaction row and its risk alert will therefore commit or roll back atomically, and count queries can include the current persisted event.

Regression coverage will verify that the configured threshold creates an alert on the threshold event, not the following event.

### Metrics identity

Use one consistent tag schema for `transaction.processing.duration`. Remove the untagged registration and keep the processing aspect's `method`, `type`, and `status` tags. Monitoring queries continue using the same metric name.

### Browser E2E coverage

Admin tests will explicitly enter the Operations Console before asserting admin navigation. Customer and admin workflows will use a 30-second expectation timeout appropriate for the measured Docker-backed response time. Assertions remain outcome-based; no arbitrary sleeps will be added.

## Testing

Implementation follows red-green-refactor:

1. Add or adjust focused tests that fail for the risk transaction propagation and metric schema.
2. Run each focused test and confirm the expected failure.
3. Apply the smallest production change and rerun focused tests.
4. Run the complete frontend, account-service, and transaction-service suites.
5. Rebuild/restart the affected live services.
6. Run customer and admin Playwright workflows.
7. Repeat the live rapid-transfer risk threshold, monitoring, and cross-console health checks.

## Non-goals

- No new console modules or UI redesign.
- No retry of non-idempotent financial operations.
- No broad risk-engine architecture rewrite or asynchronous event bus.
- No dependency-vulnerability or frontend bundle-size work in this repair.
