# Outcome Protection authoritative-source freshness invariant

**Status:** Implementation contract
**Date:** 2026-08-12
**Authoritative baseline:** `origin/main` at `ea7ac25500a7b89ef8bc1f9663c4a71fa46afcde`

## Invariant

An executable `RESERVE_BUFFER` repair is valid only while the authoritative state used by its exact immutable scenario version and simulation result is unchanged. Consent, activation, execution submission, and completion of a risk-MFA authorization compare a fresh canonical source fingerprint with the saved fingerprint. A mismatch returns HTTP 409 with stable code `SCENARIO_DIVERGED` and instructs the customer to refresh or re-run the scenario, select a newly replay-proven repair, and consent again.

Canonical source schema `outcome-source-v2` covers selected account identity, ownership, currency, customer-account status, ledger status/version, available balance and projection version; active schedule membership, identity, owner, accounts, amount/currency, status/version, timing, recurrence anchors, source time zone and DST policies; and protected-obligation identity, owner, state/version, amount/currency, due semantics and source projection version. Observation timestamps are evidence but are excluded from the fingerprint. Earlier schemas fail closed for executable guardrails.

## Rejection evidence and side effects

Every distinct rejection creates an append-only `SCENARIO_DIVERGED` domain event with saved/current fingerprints, schemas, scenario/version/result/guardrail/execution identifiers, stage, a hashed actor identifier, redacted component/field differences, the recovery instruction, and `moneyMoved=false`. Difference evidence never stores account or schedule values, credentials, MFA proofs, tokens, or raw requests.

Freshness rejection occurs before activation MFA consumption and before execution persistence or transfer submission. When drift is found while a transfer authorization awaits MFA, the service cancels that authorization and releases the policy reservation before returning the rejection; if cancellation itself fails, the reservation remains held and the execution remains pending for safe retry. No rejection path posts a journal, creates a debit hold or transfer, mutates a schedule or spending limit, or initiates an autonomous action.

## Execution boundary and residual race

The scenario, result, policy and execution rows are resolved and locked inside transaction-service database transactions, and the invariant is checked immediately before each external transfer boundary. Account status is read through an uncached internal account-service lookup. The current architecture cannot atomically lock transaction-service ledger/schedule rows and account-service status together with the later transfer authorization call. A narrow cross-service TOCTOU window therefore remains; the existing transfer authorization path independently rechecks ownership, status, balance, limits, holds, risk/MFA and ledger posting, so a late change still fails closed at the money-movement authority. Eliminating the window would require a cross-service version token or transactional authorization protocol.

Only `RESERVE_BUFFER` remains executable and every movement remains customer initiated and explicitly confirmed. Other repair types remain preview-only.
