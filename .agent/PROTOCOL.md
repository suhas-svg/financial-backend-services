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
