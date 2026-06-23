# Codex and Antigravity Cross-Agent Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe, Git-backed checkpoint and resume protocol that lets Codex and Google Antigravity continue the same branch in either direction.

**Architecture:** `AGENTS.md` and `GEMINI.md` are thin provider entrypoints into one shared `.agent/PROTOCOL.md`. A canonical JSON handoff plus cooperative lease are validated by dependency-free PowerShell checkpoint/resume scripts; a temporary-repository test harness verifies safety without touching application data.

**Tech Stack:** Markdown, JSON Schema Draft 2020-12, PowerShell 5.1+, Git

---

## File Map

- Create `AGENTS.md`: Codex startup rules and shared-protocol pointer.
- Create `GEMINI.md`: Antigravity startup rules and shared-protocol pointer.
- Create `.agent/PROTOCOL.md`: provider-neutral handoff contract and operator commands.
- Create `.agent/schemas/active-handoff.schema.json`: canonical handoff validation contract.
- Create `.agent/schemas/lease.schema.json`: cooperative lease validation contract.
- Create `.agent/active-handoff.json`: checked-in idle initial handoff state.
- Create `.agent/lease.json`: checked-in released initial lease state.
- Create `scripts/agent-checkpoint.ps1`: capture Git/task state and release ownership.
- Create `scripts/agent-resume.ps1`: validate state, acquire ownership, and print the continuation brief.
- Create `scripts/test-agent-handoff.ps1`: dependency-free integration tests in a temporary Git repository.
- Modify `README.md`: link the protocol and show the two primary commands.

### Task 1: Add provider-neutral contracts and entrypoints

**Files:**
- Create: `AGENTS.md`
- Create: `GEMINI.md`
- Create: `.agent/PROTOCOL.md`
- Create: `.agent/schemas/active-handoff.schema.json`
- Create: `.agent/schemas/lease.schema.json`
- Create: `.agent/active-handoff.json`
- Create: `.agent/lease.json`

- [ ] **Step 1: Add the Codex entrypoint**

Create `AGENTS.md` with these exact rules:

```markdown
# Repository Agent Instructions

GitHub and the checked-out Git branch are the source of truth.

Before continuing an existing task:

1. Read `.agent/PROTOCOL.md`.
2. Read `.agent/active-handoff.json`.
3. Run `powershell -ExecutionPolicy Bypass -File scripts/agent-resume.ps1 -Agent codex` when the handoff status is not `idle`.
4. Verify `git status`, the current branch, and recorded validation before editing.

Never let two agents edit the same worktree concurrently. Never commit secrets, raw transcripts, browser state, or credentials. Run the relevant frontend and backend checks before claiming completion.
```

- [ ] **Step 2: Add the Antigravity entrypoint**

Create `GEMINI.md` with the same rules, replacing `-Agent codex` with `-Agent antigravity` and explicitly stating that shared policy lives only in `.agent/PROTOCOL.md`.

- [ ] **Step 3: Add the protocol**

Create `.agent/PROTOCOL.md` containing:

```markdown
# Cross-Agent Handoff Protocol

## Invariants

- GitHub is the remote source of truth; branch and commit identify code state.
- One agent owns the cooperative lease at a time.
- Checkpoints never commit, push, merge, reset, delete, or execute tests.
- Resume never fetches, checks out, pulls, merges, resets, or modifies application files.
- Never place secrets, environment values, raw transcripts, browser state, or database contents in handoff fields.

## Checkpoint

Run `scripts/agent-checkpoint.ps1` after each plan task, before a provider switch, and every 20-30 minutes during long work. Review `.agent/active-handoff.json`, then intentionally commit and push the code and handoff files.

## Resume

Open the same branch in a separate worktree, pull the reviewed handoff commit, then run `scripts/agent-resume.ps1`. Resolve every warning before editing. `-Force` may replace only a non-expired cooperative lease; it never bypasses repository, branch, or commit checks.

## Recovery

After a hard quota stop, start from the last committed handoff. Inspect `git status` and `git diff`; treat unrecorded changes as untrusted until reviewed.
```

- [ ] **Step 4: Add JSON schemas and idle files**

The handoff schema must require `schemaVersion`, `taskId`, `objective`, `sourceAgent`, `targetAgent`, `repository`, `branch`, `baseBranch`, `headCommit`, `worktree`, `status`, `completed`, `remaining`, `blockers`, `changedFiles`, `validation`, `nextAction`, and `updatedAt`; disallow additional properties; constrain status to `idle`, `local_only`, and `ready_for_handoff`; and define validation objects with `command`, `workingDirectory`, `result`, `summary`, and `finishedAt`. Use conditional schema rules: `idle` permits empty agent and task fields, while `local_only` and `ready_for_handoff` require `sourceAgent` and `targetAgent` to be `codex` or `antigravity` and require non-empty task, repository, branch, commit, objective, and next-action fields.

The lease schema must require `schemaVersion`, `taskId`, `agent`, `branch`, `status`, `acquiredAt`, `expiresAt`, and `updatedAt`; disallow additional properties; and constrain status to `active` and `released`. Use conditional rules so an `active` lease requires agent `codex` or `antigravity` plus non-empty task and branch, while a released idle lease permits empty values.

Initialize `active-handoff.json` with empty strings/arrays, `status: "idle"`, and a fixed RFC 3339 epoch timestamp. Initialize `lease.json` with empty task/agent/branch fields, `status: "released"`, and epoch timestamps. These files are valid schema examples rather than an active task.

- [ ] **Step 5: Validate JSON syntax and commit**

Run:

```powershell
Get-Content .agent/active-handoff.json -Raw | ConvertFrom-Json | Out-Null
Get-Content .agent/lease.json -Raw | ConvertFrom-Json | Out-Null
Get-Content .agent/schemas/active-handoff.schema.json -Raw | ConvertFrom-Json | Out-Null
Get-Content .agent/schemas/lease.schema.json -Raw | ConvertFrom-Json | Out-Null
git diff --check
```

Expected: exit code 0 and no whitespace errors.

Commit:

```powershell
git add AGENTS.md GEMINI.md .agent
git commit -m "docs: define cross-agent handoff protocol"
```

### Task 2: Build checkpoint behavior test-first

**Files:**
- Create: `scripts/test-agent-handoff.ps1`
- Create: `scripts/agent-checkpoint.ps1`

- [ ] **Step 1: Write checkpoint integration tests**

Create a dependency-free test harness with these helpers:

```powershell
$ErrorActionPreference = 'Stop'
$script:Passed = 0
$script:Failed = 0

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Assertion failed: $Message" }
    $script:Passed++
}

function Assert-Throws([scriptblock]$Action, [string]$Pattern) {
    try { & $Action; throw 'Expected action to fail' }
    catch {
        if ($_.Exception.Message -eq 'Expected action to fail') { throw }
        Assert-True ($_.Exception.Message -match $Pattern) "Error did not match $Pattern"
    }
}
```

The harness must create a uniquely named directory beneath `[System.IO.Path]::GetTempPath()`, verify the resolved cleanup path starts with the resolved temp root, initialize Git with `git init -b main`, configure a test identity, add origin `https://github.com/suhas-svg/financial-backend-services.git`, create one commit, create `codex/handoff-test`, and copy `.agent` plus whichever agent scripts currently exist into the temporary repository.

Add failing tests that invoke `agent-checkpoint.ps1` and assert:

- clean state produces `ready_for_handoff`;
- a dirty tracked file produces `local_only` and appears in `changedFiles`;
- missing objective fails with `Objective is required`;
- a value like `token=ghp_abcdefghijklmnopqrstuvwxyz123456` fails with `secret-shaped value`;
- the output JSON parses and contains the current branch and HEAD;
- the lease becomes `released`;
- a repository path containing spaces works.

- [ ] **Step 2: Run tests to verify failure**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-agent-handoff.ps1 -CheckpointOnly
```

Expected: FAIL because `scripts/agent-checkpoint.ps1` does not exist.

- [ ] **Step 3: Implement checkpoint script**

Use this parameter contract:

```powershell
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('codex','antigravity')][string]$SourceAgent,
    [Parameter(Mandatory)][ValidateSet('codex','antigravity')][string]$TargetAgent,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$TaskId,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$Objective,
    [string[]]$Completed = @(),
    [string[]]$Remaining = @(),
    [string[]]$Blockers = @(),
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$NextAction,
    [string]$BaseBranch = 'main',
    [string]$ValidationJson = '[]'
)
```

Implement `Invoke-Git` with `& git @Arguments`, checking `$LASTEXITCODE`; resolve repository root with `git rev-parse --show-toplevel`; reject detached HEAD; normalize the GitHub repository from either HTTPS or SSH origin; collect changed files from `git status --porcelain`; parse validation JSON and require the five validation fields; scan all user-provided strings with:

```powershell
$secretPattern = '(?i)(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|(?:password|token|secret|api[_-]?key)\s*[:=]\s*\S+)'
```

Write JSON atomically using a sibling `.tmp.$PID` file and `Move-Item -Force`. Mark the lease released with the same atomic writer. Set status to `ready_for_handoff` only when `changedFiles` is empty; otherwise use `local_only`. Print Git status and literal suggested `git add`, `git commit`, and `git push` commands, but do not execute them.

- [ ] **Step 4: Run checkpoint tests**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-agent-handoff.ps1 -CheckpointOnly
```

Expected: all checkpoint assertions pass and the temporary directory is removed.

- [ ] **Step 5: Commit checkpoint implementation**

```powershell
git add scripts/agent-checkpoint.ps1 scripts/test-agent-handoff.ps1
git commit -m "feat: add portable agent checkpoints"
```

### Task 3: Build safe resume and lease behavior test-first

**Files:**
- Modify: `scripts/test-agent-handoff.ps1`
- Create: `scripts/agent-resume.ps1`

- [ ] **Step 1: Add failing resume tests**

Extend the temporary-repository harness to assert:

- matching repository, branch, and reachable checkpoint commit acquires an active two-hour lease;
- objective, completed work, remaining work, blockers, validation, and next action appear in output;
- wrong branch fails with `Branch mismatch`;
- an unreachable 40-character commit fails with `not reachable`;
- an active non-expired lease owned by the other agent fails with `Lease is active`;
- `-Force` takes over the lease and prints `FORCED LEASE TAKEOVER`;
- an expired lease can be acquired without `-Force`;
- unsupported schema version fails with `Unsupported schemaVersion`;
- a dirty worktree prints a warning but does not delete or reset files.

- [ ] **Step 2: Run tests to verify failure**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-agent-handoff.ps1 -ResumeOnly
```

Expected: FAIL because `scripts/agent-resume.ps1` does not exist.

- [ ] **Step 3: Implement resume script**

Use this parameter contract:

```powershell
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('codex','antigravity')][string]$Agent,
    [switch]$Force,
    [ValidateRange(5,1440)][int]$LeaseMinutes = 120
)
```

Load JSON with `ConvertFrom-Json`; manually validate schema version 1 and all required fields before accessing values; verify repository, branch, and ancestry with `git merge-base --is-ancestor <recorded> HEAD`; treat exit code 1 as unreachable and other nonzero codes as Git errors. Parse lease timestamps with `[DateTimeOffset]::Parse`, compare in UTC, and refuse an active unexpired lease owned by another agent unless `-Force` is present. Atomic-write a new active lease whose expiry is `UtcNow.AddMinutes($LeaseMinutes)`.

Print a structured continuation brief and warnings. Do not invoke any Git mutation command.

- [ ] **Step 4: Run the full harness**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-agent-handoff.ps1
```

Expected: every checkpoint and resume test passes, zero failures, and safe temporary cleanup is reported.

- [ ] **Step 5: Commit resume implementation**

```powershell
git add scripts/agent-resume.ps1 scripts/test-agent-handoff.ps1
git commit -m "feat: add safe cross-agent resume leases"
```

### Task 4: Document and verify the operator workflow

**Files:**
- Modify: `README.md`
- Modify: `.agent/PROTOCOL.md`

- [ ] **Step 1: Add concise README usage**

Add a `Cross-Agent Handoff` section linking `.agent/PROTOCOL.md`, with one complete checkpoint example and both resume commands:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/agent-checkpoint.ps1 `
  -SourceAgent codex -TargetAgent antigravity `
  -TaskId double-entry-ledger `
  -Objective "Implement the approved ledger MVP" `
  -Completed "Journal schema" `
  -Remaining "Reversal postings" `
  -NextAction "Implement ReversalPostingService"

powershell -ExecutionPolicy Bypass -File scripts/agent-resume.ps1 -Agent antigravity
powershell -ExecutionPolicy Bypass -File scripts/agent-resume.ps1 -Agent codex
```

State explicitly that the user must review, commit, and push checkpoints and that the scripts cannot detect quota exhaustion.

- [ ] **Step 2: Run all handoff verification**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-agent-handoff.ps1
Get-Content .agent/active-handoff.json -Raw | ConvertFrom-Json | Out-Null
Get-Content .agent/lease.json -Raw | ConvertFrom-Json | Out-Null
git diff --check
git status --short
```

Expected: handoff harness passes, JSON parses, no whitespace errors, and only intended files are changed.

- [ ] **Step 3: Run repository regression checks**

```powershell
Push-Location frontend
npm.cmd test -- --run
npm.cmd run build
Pop-Location

Push-Location account-service
.\mvnw.cmd -q test
Pop-Location

Push-Location transaction-service
.\mvnw.cmd -q test
Pop-Location
```

Expected: frontend 40 tests pass and production build succeeds; account service 63 tests pass; transaction service 316 tests pass with 52 skipped.

- [ ] **Step 4: Commit documentation**

```powershell
git add README.md .agent/PROTOCOL.md
git commit -m "docs: explain Codex Antigravity handoffs"
```

- [ ] **Step 5: Review final branch scope**

```powershell
git status -sb
git diff --stat origin/main...HEAD
git log --oneline origin/main..HEAD
```

Expected: clean branch containing the design, plan, entrypoints, protocol, schemas, state files, two scripts, test harness, and README documentation only.
