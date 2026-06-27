# Task 11 ledger verification notes

Date: 2026-06-27

Branch: `codex/double-entry-ledger-mvp`

## Container topology

The MVP ledger still runs inside `transaction-service`; it is not a third service. Task 11 live verification used the isolated Compose project `ledger-task11` and `docker-compose.task11.override.yml` to avoid replacing the normal local financial stack.

Isolated verification ports:

- account-service: `http://localhost:18080`
- transaction-service, including ledger APIs: `http://localhost:18081`
- account Postgres: `127.0.0.1:25432`
- transaction Postgres: `127.0.0.1:25433`

Normal local financial stack ports remain `8080` and `8081`.

## Live API smoke evidence

The isolated stack was rebuilt with a no-cache transaction-service image and verified healthy before the smoke run.

Ledger bootstrap was run through the admin endpoint with `maintenanceMode=true`; it seeded 9 system accounts:

- `CLEARING`, `FEE`, and `SUSPENSE`
- currencies: `USD`, `EUR`, `GBP`

Successful live journey:

- customer registration/login
- admin registration/login plus admin role grant in the isolated account DB
- two customer accounts created
- deposit posted
- deposit idempotent replay returned the same transaction
- withdrawal posted using ledger-authoritative balances
- transfer posted
- customer single and batch balance projection APIs returned balances
- customer journal API returned the deposit journal
- monthly statement generated and listed
- statement CSV endpoint returned content
- daily reconciliation completed
- admin reconciliation exception queue returned 0 exceptions

Observed live result:

```json
{
  "customer": "task11_customer_1782541875059",
  "account1": "1841",
  "account2": "1842",
  "deposit": "d8404112-8c12-40fb-917f-8af7302e178f",
  "withdrawal": "6e1cc4be-33b0-4a26-b9a1-ce45deea6bf2",
  "transfer": "ee0c551b-e331-4667-a3d0-ccc9a9ce095e",
  "balance1": 125.00,
  "balance2": 50.00,
  "journal": "cb6f9117-5390-4f72-a33b-abdd70f37eb4",
  "statement": "424fad23-507e-4f38-9bee-ea1031622d88",
  "reconciliation": "23eeb2b9-7fba-408a-9ad9-1c9b3efa6954",
  "reconciliationStatus": "COMPLETED",
  "exceptions": 0
}
```

## Browser verification

Frontend was served on `http://127.0.0.1:5174` with:

- `VITE_ACCOUNT_API_TARGET=http://localhost:18080`
- `VITE_TRANSACTION_API_TARGET=http://localhost:18081`

Customer browser verification showed:

- account `1841`: posted `$125.00`
- account `1842`: posted `$50.00`
- recent deposit, withdrawal, and transfer all `COMPLETED`

Admin browser verification showed:

- admin reconciliation page reachable at `/admin/reconciliation`
- latest run `COMPLETED`
- business date `2026-06-27`
- open exceptions `0`
- critical exceptions `0`
- browser console errors: `0`

## Regression evidence

Passing checks:

- `account-service`: `.\mvnw.cmd -q test`
  - `13` XML files, `72` tests, `0` failures, `0` errors, `0` skipped
- transaction-service focused ledger tests:
  - `.\mvnw.cmd -q "-Dtest=LedgerBootstrapServiceTest,AccountLedgerResolverTest,TransactionLedgerIntegrationTest" test`
  - `3` XML files, `11` tests, `0` failures, `0` errors, `0` skipped
- frontend:
  - `npm.cmd test -- --reporter=dot`: `4` files, `47` tests passed
  - `npm.cmd run lint`: exit `0`
  - `npm.cmd run build`: exit `0`; Vite emitted only the pre-existing chunk-size warning
- checkpoint protocol:
  - `powershell -ExecutionPolicy Bypass -File scripts\test-agent-handoff.ps1`
  - `48` assertions, `0` failures

Full transaction-service suite status:

- `.\mvnw.cmd -q test`
- `67` XML files, `395` tests, `0` failures, `6` errors, `61` skipped
- errors are from local Testcontainers Docker discovery, not ledger business logic:
  - `LedgerMigrationIntegrationTest`
  - `LedgerPostingConcurrencyIntegrationTest`
  - `CachePerformanceBenchmark`
  - all include `Previous attempts to find a Docker environment failed`

## Task 11 defects found and fixed

- Account-service migration `V6__add_currency_and_ledger_projection_mirror.sql` used `CHAR(3)` for `accounts.currency`; Hibernate expected `VARCHAR(3)`. Added migration regression test and changed to `VARCHAR(3)`.
- Production bootstrap with no legacy account source seeded no system accounts. Added regression coverage and default seeded system currencies `USD`, `EUR`, and `GBP`.
- Live-created customer accounts had no transaction-service ledger account until first posting. Added on-demand customer ledger account and projection creation from the account-service snapshot.
- Ledger-authoritative withdrawal/transfer still prechecked stale account-service balance snapshots. Added regression coverage and let locked ledger projections enforce debit availability when ledger-authoritative mode is enabled.
