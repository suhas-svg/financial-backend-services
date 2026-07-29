# Supply-chain and test governance

The controlled-beta release workflow uses immutable commit SHAs for every
GitHub Action and registry digests for every external build, runtime, database,
and cache image in the accepted path. Version comments and image tags remain
beside those immutable identifiers for human-readable update reviews.

Container changes must preserve the vulnerability scan, SPDX SBOM, dependency
scan, and full-history secret scan jobs. `scripts/verify-release-authority.ps1`
fails if an accepted action or image returns to a moving reference.

Account-service retains bounded Surefire reruns during a short adoption window
so existing intermittent tests remain visible without silently blocking every
change. `scripts/check-surefire-retries.ps1` reads Surefire XML and reports each
test that succeeds only after a rerun. Starting at 2026-08-12 00:00 UTC, any
retry-only success fails its Java verification job and therefore the aggregate
`Required Acceptance` check. Ordinary first-attempt failures continue to fail
Maven immediately under the existing test lifecycle.

When retry-only evidence appears:

1. Treat the named test as unstable even though Maven eventually passed.
2. Preserve the report and diagnose shared state, timing, ordering, and
   environment assumptions.
3. Fix or quarantine only through a reviewed change with equivalent deterministic
   coverage; do not increase the retry count or move the enforcement date.
