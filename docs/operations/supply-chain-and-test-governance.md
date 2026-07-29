# Supply-chain and test governance

The controlled-beta release workflow uses immutable commit SHAs for every
GitHub Action and registry digests for every external build, runtime, database,
and cache image in the accepted path. Version comments and image tags remain
beside those immutable identifiers for human-readable update reviews.

Container changes must preserve the vulnerability scan, SPDX SBOM, dependency
scan, and full-history secret scan jobs. `scripts/verify-release-authority.ps1`
fails if an accepted action or image returns to a moving reference.

Surefire reruns remain diagnostic only. `scripts/check-surefire-retries.ps1`
reads Surefire XML and immediately fails the Java verification job when a test
succeeds only after a rerun. There is no reporting-only grace period. Ordinary
first-attempt failures continue to fail Maven under the existing test lifecycle,
and retry-only success fails the aggregate `Required Acceptance` check.

When retry-only evidence appears:

1. Treat the named test as unstable even though Maven eventually passed.
2. Preserve the report and diagnose shared state, timing, ordering, and
   environment assumptions.
3. Fix or quarantine only through a reviewed change with equivalent deterministic
   coverage; do not increase the retry count or weaken immediate enforcement.
