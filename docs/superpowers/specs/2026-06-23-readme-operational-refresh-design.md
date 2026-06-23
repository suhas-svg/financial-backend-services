# README Operational Refresh Design

## Goal

Bring the root README in line with the merged customer/admin console, backend, Docker, and validation workflows without rewriting the accurate API reference.

## Scope

- Preserve the existing feature and endpoint documentation.
- Document the customer and admin route groups explicitly.
- Replace vague backend startup guidance with reproducible PowerShell commands for `docker-compose.codex.yml` and its local port override.
- Explain required JWT secrets without committing real secret values.
- Clarify that public registration creates `ROLE_USER` accounts and that admin E2E credentials must match a locally seeded `ROLE_ADMIN` account.
- Replace the partial transaction-service test command with the full test-suite command.
- Record the current verified test totals and the remaining dependency and frontend bundle warnings.
- Replace stale screenshot-directory claims with a summary of the verified cross-console workflow.

## Constraints

- Do not add or expose real credentials.
- Do not change application code, runtime configuration, or API behavior.
- Keep commands Windows/PowerShell-first because that is the verified local workflow.
- Keep time-sensitive test totals clearly labeled as the current baseline rather than permanent guarantees.

## Validation

- Confirm every referenced file and directory exists.
- Compare ports, environment-variable names, routes, and test scripts with source configuration.
- Run Markdown-oriented repository checks available locally and inspect the final diff for README-only scope plus this design record.
