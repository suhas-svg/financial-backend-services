# Beneficiary Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add customer-owned saved recipients and reuse them in immediate and scheduled transfer creation.

**Architecture:** `account-service` persists and validates beneficiaries. `transaction-service` keeps existing transfer execution APIs. The React frontend resolves selected recipients to destination account IDs before submitting existing transfer payloads.

**Tech Stack:** Spring Boot, Spring Data JPA, Flyway, JUnit 5, Mockito, React 18, TypeScript, TanStack Query, React Hook Form, Zod, Vitest, Testing Library.

---

## File Map

- Create `account-service/src/main/java/com/suhasan/finance/account_service/entity/BeneficiaryStatus.java` for active/disabled state.
- Create `account-service/src/main/java/com/suhasan/finance/account_service/entity/Beneficiary.java` for persistence.
- Create `account-service/src/main/java/com/suhasan/finance/account_service/dto/BeneficiaryCreateRequest.java`, `BeneficiaryUpdateRequest.java`, and `BeneficiaryResponse.java`.
- Create `account-service/src/main/java/com/suhasan/finance/account_service/repository/BeneficiaryRepository.java`.
- Create `account-service/src/main/java/com/suhasan/finance/account_service/service/BeneficiaryService.java`.
- Create `account-service/src/main/java/com/suhasan/finance/account_service/controller/BeneficiaryController.java`.
- Create `account-service/src/main/resources/db/migration/V7__create_beneficiaries.sql`.
- Create backend tests for service and controller behavior.
- Modify `frontend/src/types.ts`, `frontend/src/lib/schemas.ts`, `frontend/src/lib/queries.ts`, `frontend/src/App.tsx`, `frontend/src/components/CustomerLayout.tsx`, `frontend/src/pages/MoveMoneyPage.tsx`, and `frontend/src/pages/ScheduledTransfersPage.tsx`.
- Create `frontend/src/pages/BeneficiariesPage.tsx`.
- Modify frontend tests in `frontend/src/lib/schemas.test.ts`, `frontend/src/lib/api.test.ts`, and `frontend/src/test/app.component.test.tsx`.

## Tasks

### Task 1: Backend Failing Tests

**Files:**
- Create: `account-service/src/test/java/com/suhasan/finance/account_service/service/BeneficiaryServiceTest.java`
- Create: `account-service/src/test/java/com/suhasan/finance/account_service/controller/BeneficiaryControllerTest.java`

- [ ] Write tests for create, duplicate rejection, same-owner rejection, list filter, update, disable, and controller current-user delegation.
- [ ] Run `.\mvnw.cmd -q -Dtest=BeneficiaryServiceTest,BeneficiaryControllerTest test` from `account-service`.
- [ ] Verify tests fail because beneficiary classes do not exist.

### Task 2: Backend Implementation

**Files:**
- Create all account-service beneficiary entity, DTO, repository, service, controller, and migration files listed in the file map.

- [ ] Implement only the API needed by the failing tests.
- [ ] Run `.\mvnw.cmd -q -Dtest=BeneficiaryServiceTest,BeneficiaryControllerTest test` from `account-service`.
- [ ] Verify tests pass.

### Task 3: Frontend Failing Tests

**Files:**
- Modify: `frontend/src/lib/schemas.test.ts`
- Modify: `frontend/src/lib/api.test.ts`
- Modify: `frontend/src/test/app.component.test.tsx`

- [ ] Add tests for beneficiary schema, API helper proxy mapping, sidebar route, recipient CRUD page, move-money recipient selection, and scheduled-transfer recipient selection.
- [ ] Run `npm.cmd test -- src/lib/schemas.test.ts src/lib/api.test.ts src/test/app.component.test.tsx` from `frontend`.
- [ ] Verify tests fail because frontend recipient support does not exist.

### Task 4: Frontend Implementation

**Files:**
- Modify and create frontend files listed in the file map.

- [ ] Add beneficiary types, schema, query helpers, route, sidebar item, and page.
- [ ] Add shared recipient destination selection logic to Move Money and Scheduled Transfers forms.
- [ ] Run `npm.cmd test -- src/lib/schemas.test.ts src/lib/api.test.ts src/test/app.component.test.tsx` from `frontend`.
- [ ] Verify tests pass.

### Task 5: Focused Verification

**Files:**
- No new source files.

- [ ] Run `.\mvnw.cmd -q -Dtest=BeneficiaryServiceTest,BeneficiaryControllerTest test` from `account-service`.
- [ ] Run `npm.cmd test -- src/lib/schemas.test.ts src/lib/api.test.ts src/test/app.component.test.tsx` from `frontend`.
- [ ] Run `npm.cmd run build` from `frontend`.
- [ ] Run broader tests only if focused failures indicate shared behavior changed.

## Self-Review

The plan covers the approved MVP: save, list, edit, disable, Move Money selection, and Scheduled Transfers selection. It keeps transaction-service unchanged and scoped out notification/audit expansion. There are no placeholder tasks; each task has exact files and verification commands.
