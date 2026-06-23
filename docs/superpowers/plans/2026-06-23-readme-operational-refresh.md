# README Operational Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the root README accurately describe the merged consoles, reproducible local Docker workflow, validation baseline, and known warnings.

**Architecture:** Keep the existing feature and API reference intact. Change only the operational sections of `README.md`, using repository configuration as the source of truth and retaining the approved design record as supporting documentation.

**Tech Stack:** Markdown, PowerShell, Docker Compose, Maven Wrapper, npm, GitHub Actions

---

### Task 1: Refresh local startup and console guidance

**Files:**
- Modify: `README.md:141-206`

- [ ] **Step 1: Replace vague backend startup guidance**

Document PowerShell examples that set non-production local values for `JWT_SECRET` and `INTERNAL_JWT_SECRET`, then start `docker-compose.codex.yml` together with `docker-compose.codex.override.yml` using `docker compose -f ... up --build -d`.

- [ ] **Step 2: Document service verification**

Add `docker compose ... ps` and list the verified URLs: frontend `http://127.0.0.1:5173`, account service `http://127.0.0.1:8080`, and transaction service `http://127.0.0.1:8081`.

- [ ] **Step 3: Document console routes**

List customer routes `/`, `/accounts`, `/move-money`, `/transactions`, `/disputes`, and `/notifications`; list the admin route group `/admin/*` and its accounts, monitoring, transactions, audit-log, risk-alerts, risk-cases, disputes, and investigations pages.

- [ ] **Step 4: Clarify admin account behavior**

State that registration creates `ROLE_USER`, an admin must be seeded or promoted to `ROLE_ADMIN`, and `E2E_ADMIN_USERNAME` plus `E2E_ADMIN_PASSWORD` must match that account. Do not publish a real password.

### Task 2: Refresh validation and live workflow documentation

**Files:**
- Modify: `README.md:495-589`

- [ ] **Step 1: Correct backend test commands**

Document `./mvnw.cmd -q test` for both `account-service` and `transaction-service`, alongside `npm test`, `npm run build`, and `npm run e2e` for the frontend.

- [ ] **Step 2: Record the verified baseline**

Add a dated baseline for 2026-06-23: frontend 40 tests passed, production build passed, account service 63 tests passed, and transaction service 316 tests passed with 52 skipped.

- [ ] **Step 3: Replace stale demo evidence**

Remove references to absent `frontend/demo-screenshots/` and `frontend/backend-demo-screenshots/`. Replace them with the verified cross-console workflow: customer account and money movement, fifth-transfer risk alert, admin review surfaces, and route authorization.

- [ ] **Step 4: Add known warnings**

Document the seven npm dependency vulnerabilities reported at the verified baseline and the Vite chunk-size warning for the approximately 786 kB minified application bundle.

### Task 3: Verify and publish

**Files:**
- Modify: `README.md`
- Verify: `frontend/src/App.tsx`, `frontend/package.json`, `frontend/vite.config.ts`, `docker-compose.codex.yml`, `docker-compose.codex.override.yml`, `account-service/src/main/resources/application.properties`, `transaction-service/src/main/resources/application.properties`

- [ ] **Step 1: Validate referenced paths**

Run:

```powershell
rg -o "`[^`]+`" README.md
Test-Path docker-compose.codex.yml
Test-Path docker-compose.codex.override.yml
Test-Path frontend/src/App.tsx
```

Expected: all operational files referenced by the new guidance exist.

- [ ] **Step 2: Validate configuration names and routes**

Run:

```powershell
rg -n "JWT_SECRET|INTERNAL_JWT_SECRET|ACCOUNT_SERVICE_TIMEOUT" docker-compose.codex.yml
rg -n "Route path=|path=\"" frontend/src/App.tsx
rg -n "test|build|e2e" frontend/package.json
```

Expected: documented variables, routes, and npm scripts match source configuration.

- [ ] **Step 3: Validate Markdown and scope**

Run:

```powershell
git diff --check
git diff --stat origin/main...HEAD
git status --short
```

Expected: no whitespace errors; changes are limited to the design, implementation plan, and root README.

- [ ] **Step 4: Commit the README**

```powershell
git add README.md docs/superpowers/plans/2026-06-23-readme-operational-refresh.md
git commit -m "docs: refresh local workflow guidance"
```

- [ ] **Step 5: Push and open a draft PR**

```powershell
git push -u origin codex/readme-operational-refresh
gh pr create --draft --base main --head codex/readme-operational-refresh --title "[codex] refresh README operational guidance"
```

Expected: a draft PR containing only the approved documentation refresh.
