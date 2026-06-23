# Repository Agent Instructions

GitHub and the checked-out Git branch are the source of truth. Shared handoff policy lives only in `.agent/PROTOCOL.md`.

Before continuing an existing task:

1. Read `.agent/PROTOCOL.md`.
2. Read `.agent/active-handoff.json`.
3. Run `powershell -ExecutionPolicy Bypass -File scripts/agent-resume.ps1 -Agent antigravity` when the handoff status is not `idle`.
4. Verify `git status`, the current branch, and recorded validation before editing.

Never let two agents edit the same worktree concurrently. Never commit secrets, raw transcripts, browser state, or credentials. Run the relevant frontend and backend checks before claiming completion.
