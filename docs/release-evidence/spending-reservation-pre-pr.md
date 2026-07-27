# Spending Reservation Pre-PR Verification

- Verified source commit: `26222df8d194e75163eadfc394ab8d4db90d003b`
- Workflow run: https://github.com/suhas-svg/financial-backend-services/actions/runs/30307249679
- Java 21 full verification: passed for account-service and transaction-service
- Java 22 full verification: passed for account-service and transaction-service
- Focused replay, conflict, concurrency, remote-effect, local-failure, reconciliation, and orphan-expiration tests: passed
- Fresh PostgreSQL migrations and legacy reservation upgrade scenario: passed
- Docker synthetic topology and real cross-service API/browser demonstration: passed
- Canonical concurrency gate includes spending-limit reservation coverage

This file is generated only after every required pre-PR gate succeeds.
