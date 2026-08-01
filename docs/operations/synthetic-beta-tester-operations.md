# Synthetic-beta tester operations

## Terms and privacy

This environment is synthetic and must remain visibly labelled `SYNTHETIC ENVIRONMENT ? NO REAL MONEY` on every route. Do not enter real names, account numbers, credentials reused elsewhere, payment details, government identifiers, health information, or other personal/confidential data. Activity, audit events, test feedback, screenshots, and operational evidence may be retained for defect diagnosis. Testers consent only to this bounded synthetic evaluation; no consent to real-money processing or provider sharing is implied.

## Known limitations

- No real providers, payment rails, executable FX, KMS/IdP certification, multi-tenancy, real customer data, or legal/regulatory approval.
- Balances come from the internal immutable synthetic ledger; integrations and notifications use local/certification adapters.
- Restore and failure-drill evidence is synthetic. Disaster-recovery RTO/RPO and external alert delivery are not certified.
- Accessibility automation covers critical flows but does not replace assistive-technology and human usability review.
- Seven-day soak readiness is incomplete until a continuous/restartable 168-hour receipt says `completed=true`.

## Onboarding and reset

Use the one-time operator bootstrap and MFA flow in the Phase 2 sandbox runbook. Use only issued synthetic identities. Seed through the idempotent, MFA-protected sandbox endpoint. To reset, obtain operations approval, preserve requested defect evidence, set `SANDBOX_PROFILE=synthetic-sandbox`, and run `reset-reseed-synthetic-sandbox.ps1 -Confirmation "RESET SYNTHETIC SANDBOX"`. Reset destroys only the named sandbox volumes and requires re-bootstrap/MFA/reseed.

## Support ownership

The controlled-beta operations owner handles access/reset, service health, reconciliation, backups, and incident triage. The product/test owner owns scenarios and acceptance. Security/privacy reviewers own suspected data exposure. External provider and legal approvals have no in-repository owner and remain blocked until formally supplied. Do not ask a tester to bypass MFA, certificate warnings, role checks, environment labelling, or fail-closed gates.

## Feedback and bug reports

Submit feedback through the repository's approved issue workflow. Include: synthetic environment classification, UTC timestamp, route, role (never token), concise steps, expected/actual behavior, correlation or transaction ID if already visible, accessibility technology/browser, and a redacted screenshot when useful. Mark severity and whether money/ledger, authorization, privacy, or availability invariants may be affected. Never attach tokens, passwords, secret references, raw database dumps, browser state, or unredacted transcripts. For a suspected financial-integrity or authorization defect, stop the scenario and notify the operations owner before retrying.
