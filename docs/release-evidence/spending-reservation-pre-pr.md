# Spending Reservation Pre-PR Verification

- Verified source commit: `6bde796fd8474d5e760edba02d0c30e83209dc14`
- Workflow run: https://github.com/suhas-svg/financial-backend-services/actions/runs/30320908263
- Java 21 full verification: passed for account-service and transaction-service
- Java 22 full verification: passed for account-service and transaction-service
- Focused replay, conflict, concurrency, remote-effect, local-failure, reconciliation, and orphan-expiration tests: passed
- Fresh PostgreSQL migrations and legacy reservation upgrade scenario: passed
- Docker synthetic topology and real cross-service API/browser demonstration: passed
- Canonical concurrency gate includes spending-limit reservation coverage

This file is generated only after every required pre-PR gate succeeds.
