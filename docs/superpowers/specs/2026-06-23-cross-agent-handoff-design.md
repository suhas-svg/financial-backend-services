# Codex and Antigravity Cross-Agent Handoff Design

## Goal

Allow Codex and Google Antigravity to continue the same repository task in either direction with minimal lost context, while keeping GitHub and the active Git branch as the source of truth.

## Product Boundary

Codex and Antigravity do not share native conversation state, approvals, memories, or usage-limit events. The handoff therefore uses portable repository artifacts and Git state. It does not attempt to copy private transcripts, detect a provider limit automatically, or launch one provider from the other.

A user initiates the switch before or after a provider limit. Frequent milestone checkpoints reduce the impact of a sudden hard stop. Codex `/status` can be used to inspect current rate limits, but no undocumented usage-exhaustion hook is assumed.

## Design Principles

- GitHub is the remote source of truth.
- The active branch and commit identify the code state.
- Exactly one agent holds the write lease at a time.
- Durable repository rules are separate from temporary task state.
- Handoff files contain no passwords, tokens, cookies, database dumps, or raw transcripts.
- Scripts never reset, delete, force-push, merge, or silently commit application changes.
- Incoming agents verify state before editing.
- Both directions use the same protocol.

## Repository Layout

```text
AGENTS.md
GEMINI.md
.agent/
  PROTOCOL.md
  active-handoff.json
  lease.json
  schemas/
    active-handoff.schema.json
    lease.schema.json
scripts/
  agent-checkpoint.ps1
  agent-resume.ps1
  test-agent-handoff.ps1
```

### `AGENTS.md`

Codex's repository entrypoint. It contains concise project rules and requires Codex to read `.agent/PROTOCOL.md` and `.agent/active-handoff.json` before continuing an existing task.

### `GEMINI.md`

Antigravity's repository entrypoint. It mirrors only the provider-neutral startup requirements and points to the same protocol and active handoff. Shared instructions remain in `.agent/PROTOCOL.md` to prevent the two entrypoints from drifting.

### `.agent/PROTOCOL.md`

The durable human-readable operating contract. It defines ownership, checkpoint, resume, validation, Git, secret-handling, stale-lease, and conflict rules.

### `.agent/active-handoff.json`

The canonical temporary task state. It is tracked in Git so either provider can receive it through the same branch. Required fields are:

```json
{
  "schemaVersion": 1,
  "taskId": "double-entry-ledger",
  "objective": "Implement the approved ledger MVP",
  "sourceAgent": "codex",
  "targetAgent": "antigravity",
  "repository": "suhas-svg/financial-backend-services",
  "branch": "codex/double-entry-ledger",
  "baseBranch": "main",
  "headCommit": "0123456789abcdef",
  "worktree": "C:/path/to/worktree",
  "status": "ready_for_handoff",
  "completed": ["Added journal schema"],
  "remaining": ["Implement reversal postings"],
  "blockers": [],
  "changedFiles": ["transaction-service/src/main/..."],
  "validation": [
    {
      "command": ".\\mvnw.cmd -q test",
      "workingDirectory": "transaction-service",
      "result": "passed",
      "summary": "316 passed, 52 skipped",
      "finishedAt": "2026-06-23T12:00:00Z"
    }
  ],
  "nextAction": "Implement ReversalPostingService from the approved plan",
  "updatedAt": "2026-06-23T12:05:00Z"
}
```

Arrays are used for completed, remaining, blockers, changed files, and validation so an incoming agent can consume the file without parsing prose. `headCommit` records the code snapshot at checkpoint time. A dirty worktree is allowed, but the dirty file list must be recorded and the handoff status becomes `local_only` until the user commits and pushes it.

### `.agent/lease.json`

The cooperative single-writer lease:

```json
{
  "schemaVersion": 1,
  "taskId": "double-entry-ledger",
  "agent": "antigravity",
  "branch": "codex/double-entry-ledger",
  "status": "active",
  "acquiredAt": "2026-06-23T12:10:00Z",
  "expiresAt": "2026-06-23T14:10:00Z",
  "updatedAt": "2026-06-23T12:10:00Z"
}
```

The lease is cooperative rather than a distributed lock. A two-hour expiry prevents permanent deadlock after a provider stops unexpectedly. Taking over a non-expired lease requires an explicit `-Force` flag and produces a visible warning.

## Scripts

### `agent-checkpoint.ps1`

Inputs include source agent, target agent, task identifier, objective, completed items, remaining items, blockers, next action, and optional validation results. The script:

1. Verifies it is inside a Git worktree.
2. Reads the repository, branch, base branch, HEAD, worktree path, and changed files.
3. Validates required non-secret fields.
4. Rejects obvious secret-shaped input keys and values.
5. Writes `.agent/active-handoff.json` atomically through a temporary file.
6. Marks the current lease `released`.
7. Reports whether the checkpoint is `ready_for_handoff` or `local_only`.
8. Prints the exact Git status and suggested commit/push commands without executing them.

The script never runs tests implicitly. Validation entries describe commands already executed by the outgoing agent, avoiding surprising long-running work during a limit-sensitive checkpoint.

### `agent-resume.ps1`

Inputs include the incoming agent name and optional `-Force`. The script:

1. Loads and schema-validates the active handoff and lease.
2. Verifies the current repository and branch match the handoff.
3. Verifies the recorded commit is reachable from current HEAD.
4. Warns when the worktree is dirty or remote tracking is missing.
5. Refuses a non-expired lease owned by another agent unless `-Force` is supplied.
6. Acquires a new two-hour lease atomically.
7. Prints objective, completed work, remaining work, blockers, validation evidence, changed files, and the exact next action.

It does not fetch, checkout, pull, reset, merge, or modify application files. The incoming agent or user remains in control of Git mutations.

### `test-agent-handoff.ps1`

A dependency-free PowerShell test harness creates a temporary Git repository and verifies:

- clean checkpoint creation;
- dirty worktree detection;
- required-field validation;
- atomic JSON output;
- secret-shaped value rejection;
- released lease behavior;
- valid lease acquisition;
- active-lease refusal;
- forced takeover warning;
- stale lease takeover;
- wrong branch refusal;
- unreachable checkpoint commit refusal;
- paths containing spaces;
- JSON schema conformance.

The harness deletes only its own resolved temporary directory after verifying that the path is beneath the operating-system temp directory.

## Handoff Workflow

### Codex to Antigravity

1. Codex finishes the smallest safe unit of work.
2. Codex runs relevant tests and records the results.
3. Codex runs `agent-checkpoint.ps1 -SourceAgent codex -TargetAgent antigravity ...`.
4. The user reviews changes, commits, and pushes the branch and handoff files.
5. Antigravity opens the same branch in its own worktree.
6. Antigravity reads `GEMINI.md`, runs `agent-resume.ps1 -Agent antigravity`, verifies the output, and continues from `nextAction`.

### Antigravity to Codex

The same sequence is used with the agent names reversed. Codex reads `AGENTS.md`, runs the resume script, verifies repository state, and continues.

## Checkpoint Frequency

Agents checkpoint after each completed plan task, before a long test suite, before a provider switch, and approximately every 20 to 30 minutes during long work. Checkpoints are cheap state snapshots; Git commits remain intentional review boundaries.

## Failure Handling

- **Hard usage stop before checkpoint:** the incoming agent starts from the last committed handoff, inspects `git status` and `git diff`, and treats unrecorded changes as untrusted until reviewed.
- **Dirty worktree:** resume is allowed only after an explicit warning; no automatic cleanup occurs.
- **Branch mismatch:** resume stops and prints the expected and actual branch.
- **Commit mismatch:** resume stops if the recorded commit is not an ancestor of HEAD.
- **Stale lease:** the incoming agent may acquire it after expiry and records the takeover time.
- **Conflicting active lease:** resume stops unless the user explicitly authorizes `-Force`.
- **Schema mismatch:** scripts fail closed with the unsupported schema version.
- **Missing remote:** local handoff still works, but the script labels it local-only and does not claim GitHub continuity.

## Security

- No environment values, command history, browser state, credentials, or transcript bodies are captured.
- Validation commands and summaries are allowlisted as plain text and must not contain secret values.
- Handoff JSON is encoded as UTF-8 and written atomically.
- Scripts use literal paths and argument arrays rather than dynamically evaluating user strings.
- `-Force` changes only lease ownership; it never overrides Git safety checks.
- Worktree paths are informational and are not assumed to be identical across agents.

## Scope Exclusions

- Automatic detection of Codex or Antigravity quota exhaustion.
- Automatic provider launching or UI control.
- Native conversation transcript conversion.
- Automatic test execution, commits, pushes, merges, resets, or branch deletion.
- A hosted coordination server, database, MCP server, or secrets manager.
- Simultaneous multi-agent edits to one worktree.

## Acceptance Criteria

- Either agent can create a valid checkpoint from any project worktree.
- Either agent can safely resume the checkpoint from the matching branch.
- Wrong branches, unreachable commits, conflicting leases, unsupported schemas, and secret-shaped values fail closed.
- No script performs destructive Git operations or publishes changes.
- The test harness passes on Windows PowerShell.
- Existing frontend, account-service, and transaction-service test suites remain unchanged and passing.
