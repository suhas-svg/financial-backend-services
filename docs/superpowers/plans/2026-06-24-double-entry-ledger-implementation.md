# Immutable Double-Entry Ledger MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `transaction-service` the authoritative immutable double-entry ledger, preserve existing APIs through a versioned `account-service` mirror, and add reconciliation, statements, and customer/admin workflows.

**Architecture:** `transaction-service` stores immutable journals, postings, append-only lifecycle events, transactional balance projections, reconciliation, statements, and a mirror outbox. `account-service` retains account identity/status/currency and applies only monotonic projection updates for legacy response compatibility. Existing money-movement routes delegate to the ledger posting engine without adding a third service.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL, JUnit 5, Mockito, Testcontainers, React 18, TypeScript, TanStack Query, Vitest, Testing Library, Playwright, Docker Compose.

---

## File structure

New ledger code lives below `transaction-service/src/main/java/com/suhasan/finance/transaction_service/ledger/` and is split by aggregate responsibility:

- `domain/`: immutable journal/account/posting/state types and projection rules.
- `repository/`: persistence interfaces and lock-oriented queries.
- `service/`: posting, reversal, projection, mirror, reconciliation, and statement use cases.
- `web/`: customer/admin DTOs and controllers.

Existing `TransactionServiceImpl` remains the compatibility orchestration boundary and delegates financial mutation to `LedgerPostingService`. It must not gain journal arithmetic.

## Task 1: Account currency and versioned compatibility mirror

**Files:**
- Create: `account-service/src/main/resources/db/migration/V6__add_currency_and_ledger_projection_mirror.sql`
- Create: `account-service/src/main/java/com/suhasan/finance/account_service/dto/LedgerProjectionUpdateRequest.java`
- Modify: `account-service/src/main/java/com/suhasan/finance/account_service/entity/Account.java`
- Modify: `account-service/src/main/java/com/suhasan/finance/account_service/dto/AccountRequest.java`
- Modify: `account-service/src/main/java/com/suhasan/finance/account_service/dto/AccountResponse.java`
- Modify: `account-service/src/main/java/com/suhasan/finance/account_service/mapper/AccountMapper.java`
- Modify: `account-service/src/main/java/com/suhasan/finance/account_service/controller/InternalAccountController.java`
- Modify: `account-service/src/main/java/com/suhasan/finance/account_service/service/AccountService.java`
- Test: `account-service/src/test/java/com/suhasan/finance/account_service/migration/LedgerProjectionMirrorMigrationTest.java`
- Test: `account-service/src/test/java/com/suhasan/finance/account_service/service/AccountServiceTest.java`
- Test: `account-service/src/test/java/com/suhasan/finance/account_service/security/AccountServiceSecurityConfigTest.java`

- [x] **Step 1: Write migration tests for USD backfill and mirror defaults**

Add an integration test that inserts a legacy account, runs the migration, and asserts:

```java
assertThat(row.currency()).isEqualTo("USD");
assertThat(row.pendingBalance()).isEqualByComparingTo("0.00");
assertThat(row.ledgerProjectionVersion()).isZero();
```

- [x] **Step 2: Run the migration test and verify RED**

Run: `account-service\mvnw.cmd -q -Dtest=LedgerProjectionMirrorMigrationTest test`

Expected: FAIL because V6 and the new columns do not exist.

- [x] **Step 3: Add V6 with immutable currency and mirror columns**

Use PostgreSQL definitions:

```sql
ALTER TABLE accounts ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE accounts ADD COLUMN pending_balance NUMERIC(38,2) NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN ledger_projection_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN ledger_projection_synced_at TIMESTAMP;
ALTER TABLE accounts ADD COLUMN ledger_projection_source_event_id VARCHAR(100);
ALTER TABLE accounts ADD CONSTRAINT chk_accounts_currency CHECK (currency = UPPER(currency) AND LENGTH(currency) = 3);
```

Add a trigger that rejects `currency` changes after insert.

- [x] **Step 4: Run the migration test and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [x] **Step 5: Write service tests for monotonic mirror updates**

Cover newer version application, exact replay, stale-version no-op, same-version conflict, currency mismatch, and the legacy `balance == ledgerBalance` alias.

```java
LedgerProjectionUpdateRequest request = new LedgerProjectionUpdateRequest(
        new BigDecimal("125.00"), new BigDecimal("-20.00"),
        new BigDecimal("105.00"), "USD", 7L, "event-7", now);
AccountResponse response = accountService.applyLedgerProjection(1L, request);
assertThat(response.getLedgerProjectionVersion()).isEqualTo(7L);
assertThat(response.getAvailableBalance()).isEqualByComparingTo("105.00");
```

- [x] **Step 6: Run the service tests and verify RED**

Run: `account-service\mvnw.cmd -q -Dtest=AccountServiceTest test`

Expected: FAIL because the projection contract and service method do not exist.

- [x] **Step 7: Implement entity, DTO, mapper, and service behavior**

Add `currency`, `pendingBalance`, `ledgerProjectionVersion`, synchronization fields, and:

```java
@Transactional
public AccountResponse applyLedgerProjection(Long accountId, LedgerProjectionUpdateRequest request) {
    Account account = accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    request.requireMatchingCurrency(account.getCurrency());
    if (request.version() < account.getLedgerProjectionVersion()) return accountMapper.toDto(account);
    if (request.version() == account.getLedgerProjectionVersion()) {
        request.requireExactReplay(account);
        return accountMapper.toDto(account);
    }
    account.applyProjection(request);
    return accountMapper.toDto(accountRepository.save(account));
}
```

- [x] **Step 8: Add the internal endpoint and authorization tests**

Implement `PUT /api/internal/accounts/{id}/ledger-projection`. Assert unauthenticated `401`, user `403`, and internal/admin success.

- [x] **Step 9: Run account-service regression tests**

Run: `account-service\mvnw.cmd -q test`

Expected: all tests pass.

- [x] **Step 10: Checkpoint and commit**

Run the repository checkpoint with validation, review files, then commit with `feat(account): add ledger projection mirror contract` and push.

## Task 2: Ledger database invariants

**Files:**
- Create: `transaction-service/src/main/resources/db/migration/V12__create_ledger_core.sql`
- Create: `transaction-service/src/main/resources/db/migration/V13__create_ledger_projections_and_outbox.sql`
- Create: `transaction-service/src/main/resources/db/migration/V14__fix_transaction_processing_states.sql`
- Test: `transaction-service/src/test/java/com/suhasan/finance/transaction_service/ledger/repository/LedgerMigrationIntegrationTest.java`

- [x] **Step 1: Write PostgreSQL migration tests**

Test system-account uniqueness per currency, balanced-journal enforcement, one-currency journals, positive posting amounts, idempotency uniqueness, and update/delete rejection after insertion.

- [x] **Step 2: Run migration tests and verify RED**

Run: `transaction-service\mvnw.cmd -q -Dtest=LedgerMigrationIntegrationTest test`

Expected: FAIL because ledger relations are absent.

- [x] **Step 3: Implement V12 ledger core**

Create `ledger_accounts`, `journal_transactions`, `journal_postings`, `journal_state_events`, `journal_links`, and `ledger_idempotency_claims`. Use deferred constraint triggers for balance/currency checks and immutable-row triggers for journal tables.

- [x] **Step 4: Implement V13 projections and outbox**

Create `ledger_balance_projections` and `ledger_projection_outbox` with monotonic versions, retry metadata, unique source event IDs, and indexes for due messages.

- [x] **Step 5: Implement V14 processing-state repair**

Replace the existing check constraint with all Java enum values: `INITIATED`, `HOLD_PLACED`, `HOLD_CAPTURED`, `HOLD_RELEASED`, `DEBIT_APPLIED`, `CREDIT_APPLIED`, `COMPLETED`, `COMPENSATED`, and `MANUAL_ACTION_REQUIRED`.

- [x] **Step 6: Run migration tests and full transaction tests**

Run the Step 2 command, then `transaction-service\mvnw.cmd -q test`. Expected: PASS.

- [x] **Step 7: Checkpoint and commit**

Commit with `feat(ledger): enforce immutable balanced journal schema` and push.

## Task 3: Ledger domain and projection engine

**Files:**
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/ledger/domain/LedgerAccount.java`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/ledger/domain/JournalTransaction.java`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/ledger/domain/JournalPosting.java`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/ledger/domain/JournalStateEvent.java`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/ledger/domain/LedgerBalanceProjection.java`
- Create enum files for account kind, direction, journal type, and journal state.
- Create focused Spring Data repositories under `ledger/repository/`.
- Test: `transaction-service/src/test/java/com/suhasan/finance/transaction_service/ledger/domain/LedgerBalanceProjectionTest.java`
- Test: `transaction-service/src/test/java/com/suhasan/finance/transaction_service/ledger/domain/JournalDraftTest.java`

- [x] **Step 1: Write failing projection tests**

Assert pending debit reduces available, pending credit does not increase available, post moves pending to posted, fail releases pending, and reversal applies the opposite posted effect.

```java
projection.reserveDebit(new BigDecimal("25.00"), 1L);
assertThat(projection.getPendingDebits()).isEqualByComparingTo("25.00");
assertThat(projection.getAvailableBalance()).isEqualByComparingTo("75.00");
```

- [x] **Step 2: Verify RED**

Run: `transaction-service\mvnw.cmd -q -Dtest=LedgerBalanceProjectionTest,JournalDraftTest test`

- [x] **Step 3: Implement minimal domain rules and repositories**

Keep arithmetic inside `LedgerBalanceProjection`; keep cross-posting balance validation in an immutable `JournalDraft` value object. Repository lock query:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from LedgerBalanceProjection p where p.ledgerAccountId in :ids order by p.ledgerAccountId")
List<LedgerBalanceProjection> lockAllOrdered(Collection<UUID> ids);
```

- [x] **Step 4: Verify GREEN and full suite**

Run the Step 2 command and `transaction-service\mvnw.cmd -q test`.

- [x] **Step 5: Checkpoint and commit**

Commit with `feat(ledger): add journal domain and balance projections` and push.

## Task 4: Idempotent posting and reversal service

**Files:**
- Create: `ledger/service/LedgerPostingService.java`
- Create: `ledger/service/JournalCommand.java`
- Create: `ledger/service/JournalResult.java`
- Create: `ledger/service/JournalRequestFingerprint.java`
- Create: `ledger/service/SystemLedgerAccountService.java`
- Test: `ledger/service/LedgerPostingServiceTest.java`
- Test: `ledger/service/LedgerPostingConcurrencyIntegrationTest.java`

- [x] **Step 1: Write failing posting tests**

Cover balanced transfer, same-currency enforcement, insufficient available funds, deterministic locks, idempotent replay, mismatched replay conflict, pending-to-posted, pending-to-failed, and compensating reversal.

- [x] **Step 2: Verify RED**

Run: `transaction-service\mvnw.cmd -q -Dtest=LedgerPostingServiceTest test`

- [x] **Step 3: Implement claim and pending creation transaction**

Expose:

```java
JournalResult createPending(JournalCommand command);
JournalResult post(UUID journalId, String actor);
JournalResult fail(UUID journalId, String actor, String reason);
JournalResult reverse(UUID journalId, String actor, String reason, String idempotencyKey);
```

Acquire projections in sorted ID order, enforce request fingerprints, insert balanced postings/state event, and update projections atomically.

- [x] **Step 4: Verify GREEN**

Run the Step 2 command.

- [x] **Step 5: Write and run concurrency tests**

Use PostgreSQL Testcontainers and two executor threads. Assert only one duplicate key posts and concurrent debits cannot make available balance negative.

- [x] **Step 6: Run full suite, checkpoint, and commit**

Commit with `feat(ledger): add idempotent posting and compensating reversal` and push.

## Task 5: Existing transaction workflow integration

**Files:**
- Modify: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/TransactionServiceImpl.java`
- Modify: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/entity/Transaction.java`
- Modify: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/dto/TransactionResponse.java`
- Modify: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/client/ResilientAccountServiceClient.java`
- Create: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/ledger/service/AccountLedgerResolver.java`
- Test existing transaction service/controller/reversal tests.
- Create: `ledger/service/TransactionLedgerIntegrationTest.java`

- [x] **Step 1: Write failing compatibility tests**

Assert existing deposit, withdrawal, transfer, and reversal routes produce expected response shapes plus `journalId`, while financial changes come from ledger projections and account ownership/status/currency still comes from `account-service`.

- [x] **Step 2: Verify RED**

Run focused existing controller/service tests plus `TransactionLedgerIntegrationTest`.

- [x] **Step 3: Delegate workflows to the ledger**

Resolve ledger accounts before posting. Map deposit, withdrawal, transfer, and reversal to standard posting templates. Preserve transaction IDs, audit/risk calls, notifications, limits, and statuses. Stop calling legacy balance/hold mutation endpoints when `ledger.authoritative=true`.

- [x] **Step 4: Add feature-gated deployment compatibility**

Default tests and local development to ledger authority after migrations. Permit `ledger.authoritative=false` only for pre-cutover deployment; never dual-post.

- [x] **Step 5: Verify focused and full tests**

Run both service suites. Expected: all existing and new tests pass.

- [x] **Step 6: Checkpoint and commit**

Commit with `feat(transactions): route money movement through ledger` and push.

## Task 6: Projection mirror outbox

**Files:**
- Create: `ledger/service/LedgerProjectionOutboxService.java`
- Create: `ledger/service/LedgerProjectionOutboxDispatcher.java`
- Create: `V16__relax_projection_outbox_source_event_uniqueness.sql`
- Modify account-service client DTOs/methods.
- Modify metrics configuration.
- Test: `ledger/service/LedgerProjectionOutboxDispatcherTest.java`
- Test: `ledger/service/LedgerProjectionOutboxServiceTest.java`

- [x] **Step 1: Write failing delivery tests**

Assert successful delivery, retry/backoff, exact replay, stale version handling, terminal currency conflict, and one account failure not blocking other due messages.

- [x] **Step 2: Verify RED**

Run the focused dispatcher test.

- [x] **Step 3: Implement transactional enqueue and scheduled delivery**

Enqueue in the same transaction as projection changes. Claim due rows with `FOR UPDATE SKIP LOCKED`; send monotonic versions; record attempts, next attempt, delivery timestamp, and sanitized last error.

- [x] **Step 4: Add bounded metrics and logs**

Record backlog, oldest age, retry count, and failures without account/journal IDs as metric tags.

- [x] **Step 5: Verify, checkpoint, and commit**

Commit with `feat(ledger): deliver versioned account balance mirrors` and push.

## Task 7: Customer balance and journal APIs

**Files:**
- Create DTOs/controllers/services under `ledger/web/` for balance batch and journal detail.
- Modify `transaction-service/src/main/java/com/suhasan/finance/transaction_service/security/SecurityConfig.java`.
- Test: `ledger/web/CustomerLedgerControllerTest.java`
- Modify frontend types/queries/dashboard/accounts/transaction detail.

- [ ] **Step 1: Write failing ownership and response tests**

Assert customer ownership, admin/internal access, customer-safe journal redaction, batch ordering, and `401/403/404` behavior.

- [ ] **Step 2: Verify RED**

Run the focused controller test.

- [ ] **Step 3: Implement customer ledger APIs**

Add list/balance/batch/journal endpoints from the design. Resolve ownership from authentication and stored `ownerId` only.

- [ ] **Step 4: Write failing frontend projection tests**

Assert available is primary, posted/pending are shown, pending credits are labeled unavailable, and legacy fields render during deployment skew.

- [ ] **Step 5: Implement frontend queries and views**

Add typed projection APIs and targeted TanStack Query keys; preserve existing routes.

- [ ] **Step 6: Verify backend/frontend suites, checkpoint, and commit**

Commit with `feat(ui): show authoritative ledger balances` and push.

## Task 8: Reconciliation and admin exception queue

**Files:**
- Create: `V17__create_reconciliation.sql`
- Create reconciliation entities/repositories/services/controllers under `ledger/`.
- Modify audit, investigation, metrics, and security integration.
- Create: `frontend/src/pages/AdminReconciliationPage.tsx`
- Modify admin layout/app/types/queries/tests.

- [x] **Step 1: Write failing reconciliation tests**

Cover each designed check, advisory-lock single execution, stable exception dedupe, deterministic severity, assignment/version conflicts, required resolution notes, and no ledger mutation on exception resolution.

- [x] **Step 2: Verify RED**

Run focused reconciliation tests.

- [x] **Step 3: Implement schema and reconciliation engine**

Recompute journal/projection controls from immutable sources. Persist immutable run/check results and deduplicated operational exceptions.

- [x] **Step 4: Add authorized admin APIs and audit links**

Implement run/list/detail/assignment/status/notes endpoints with optimistic versions and admin-only mutations.

- [x] **Step 5: Write failing and then passing frontend tests**

Cover summaries, filters, detail, assignment, notes, status changes, links, and manual rerun.

- [x] **Step 6: Verify, checkpoint, and commit**

Commit with `feat(reconciliation): add daily controls and exception queue` and push.

## Task 9: Immutable monthly statements

**Files:**
- Create: `V18__create_monthly_statements.sql`
- Create statement entities/repositories/service/controller/exporter.
- Create: `frontend/src/pages/StatementsPage.tsx`
- Modify customer layout/app/types/queries/tests.

- [x] **Step 1: Write failing statement tests**

Cover UTC periods, idempotent generation, posted-only lines, opening/closing arithmetic, late correction in next period, immutable versions, ownership, and CSV escaping.

- [x] **Step 2: Verify RED**

Run focused statement tests.

- [x] **Step 3: Implement generation, APIs, and CSV**

Generate from posted journal event watermark; store account snapshot and ordered lines; never update a generated version.

- [x] **Step 4: Write failing and passing frontend tests**

Cover statement list, detail totals/lines, empty state, and CSV action.

- [x] **Step 5: Verify, checkpoint, and commit**

Commit with `feat(statements): add immutable monthly customer statements` and push.

## Task 10: Bootstrap, cutover controls, and observability

**Files:**
- Create ledger bootstrap service/admin command and tests.
- Modify application properties, Docker Compose, monitoring config, and dashboards.
- Create operational runbook under `docs/operations/`.

- [ ] **Step 1: Write failing bootstrap tests**

Assert idempotent account import, per-currency system seeds, balanced opening journals, refusal with unresolved legacy holds/processing transactions, and parity verification before authority enablement.

- [ ] **Step 2: Verify RED, implement, and verify GREEN**

Keep bootstrap disabled by default and require an explicit admin/internal command plus maintenance mode.

- [ ] **Step 3: Add metrics and alert rules**

Implement the bounded metrics and critical alerts specified in the design.

- [ ] **Step 4: Document exact cutover and rollback boundary**

Include commands, preconditions, evidence capture, zero-critical-exception gate, and post-authority forward-fix rule.

- [ ] **Step 5: Verify, checkpoint, and commit**

Commit with `feat(ledger): add controlled bootstrap and operations telemetry` and push.

## Task 11: Full regression, Docker, live, and browser verification

**Files:**
- Modify E2E fixtures/specs and Docker verification scripts only where required.
- Update root/service READMEs and API documentation.

- [ ] **Step 1: Run all static and unit verification**

Run account tests, transaction tests, frontend tests/lint/build, and handoff tests. Record exact counts.

- [ ] **Step 2: Run clean-volume Docker migration verification**

Build both services, start PostgreSQL/Redis/services, confirm health, and inspect Flyway versions without exposing secrets.

- [ ] **Step 3: Run live API journeys**

Verify register/login/account/deposit/withdraw/transfer/reversal, authoritative projections, mirror synchronization, reconciliation, exception authorization, and statements.

- [ ] **Step 4: Run Playwright customer/admin journeys**

Verify existing functionality plus balances, statements, and reconciliation UI.

- [ ] **Step 5: Run final reconciliation acceptance gate**

Require zero unexplained critical differences; document any intentionally seeded/resolved test exception.

- [ ] **Step 6: Update documentation and final checkpoint**

Commit with `docs: document ledger operations and verification` and push.

## Task 12: Review and draft PR publication

- [ ] **Step 1: Run verification-before-completion checks**

Re-run all commands supporting final claims and inspect `git diff origin/main...HEAD` plus worktree status.

- [ ] **Step 2: Use requesting-code-review skill**

Review requirements, security boundaries, migration safety, concurrency, and backward compatibility. Address every validated issue with TDD.

- [ ] **Step 3: Use finishing-a-development-branch skill**

Confirm branch history and choose the user-approved integration route.

- [ ] **Step 4: Publish a draft PR**

Push `codex/double-entry-ledger-mvp`, open a draft PR against `main`, and include architecture, migration/cutover notes, test evidence, Docker/live evidence, UI evidence, and remaining limitations.
