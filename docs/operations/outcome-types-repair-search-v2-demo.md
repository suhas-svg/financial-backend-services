# Outcome Types and Repair Search V2 live-demo scenario

This walkthrough proves simulation and preview selection only. It must not change a schedule, spending limit, ledger journal, account balance, hold, transfer authorization, or funds.

## Preconditions

- Run the fresh PostgreSQL migrations through transaction-service `V28`.
- Keep the Balance Shield global execution kill switch disabled.
- Use a synthetic customer with one funded same-currency checking account.
- Create an active recurring rent/payment schedule that debits the checking account inside the next 30 days.
- Create a second active schedule with reference `FLEXIBLE` or `OPTIONAL`; this explicit reference is the V2 eligibility marker for schedule-shift/reduction previews.
- Record the schedule rows and versions, spending limits, ledger projection/version, journal count, transaction count, holds, and balances before the demo.

## Browser flow

1. Sign in as the synthetic customer and open `/outcome-protection`.
2. Select the funded ledger account.
3. Choose **Scheduled payment must succeed**.
4. Select the rent/payment obligation and retain a non-zero optional balance floor.
5. Add bounded assumptions/shocks that make the rent occurrence fail or break the floor.
6. Run the lab and verify the immutable obligation card shows schedule ID/version/state, source/destination ownership, amount/currency, due instant/local date, time zones, and source projection version.
7. Inspect the breaking causal chain and distinguish `PROTECTED_OBLIGATION_INSUFFICIENT_FUNDS` from `BALANCE_FLOOR_BREACH`.
8. Compare at least three eligible repair types. Expand each deterministic replay and confirm it restores both invariant labels, shows ranking factors, configured caps, and a certificate hash.
9. Expand unavailable actions and confirm the protected obligation is rejected as a repair target.
10. Select one preview draft before consent. Do not activate or execute the reserve-buffer policy.

## No-mutation proof

After simulation and selection, compare the same records captured before the demo:

- protected and optional schedule rows, versions, states, amounts, and due instants;
- customer spending-limit rows and audit events;
- ledger projection/version, journals, postings, and holds;
- transactions and transfer authorizations;
- account ledger/available balances.

Every financial and control value must be unchanged. Only append-only Outcome Protection scenario/version/result/evidence rows and the selected preview timestamp/idempotency evidence may be new.

## Expected operator evidence

- `outcome_scenario_versions` contains `outcome_type`, canonical inputs, and protected-obligation JSON.
- `outcome_simulation_results` contains engine version, source versions, candidates, replay outputs, ranking/rejection evidence, repair cap evidence, and the certificate hash.
- `outcome_guardrail_drafts` contains one ranked draft per replay-proven alternative and only the explicitly selected draft has preview-selection evidence.
- Refresh after pausing or version-changing the protected obligation creates deduplicated divergence/warning evidence without moving funds.
