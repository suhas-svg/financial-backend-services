# Repository Agent Instructions

GitHub and the checked-out Git branch are the source of truth.

Before continuing an existing task, verify `git status`, the current branch, and recorded validation before editing.

Never let two agents edit the same worktree concurrently. Never commit secrets, raw transcripts, browser state, or credentials. Run the relevant frontend and backend checks before claiming completion.
