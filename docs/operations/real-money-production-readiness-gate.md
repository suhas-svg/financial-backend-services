# Real-money production readiness evidence gate

Real-money production is fail closed until external owners provide a complete,
currently effective evidence manifest and the release operator verifies its
approved SHA-256 digest. Repository tests prove the verifier's behavior; they do
not manufacture contracts, credentials, legal decisions, or operational evidence.

## Required gates

The external manifest must satisfy
`production-readiness-evidence.schema.json` and contain exactly these approved
gates, each with distinct owner and reviewer identities, an external reference,
an evidence digest, and current approval/expiry dates:

- named providers and payment rails;
- production IdP and KMS;
- provider-specific webhook verification;
- legal, privacy, licensing, and jurisdiction approval;
- completed backup and restore evidence against RTO/RPO;
- external alerting, on-call, and incident exercises;
- sustained production-representative load and soak evidence;
- approved change and rollback governance.

Keep the manifest outside the repository and the deployment artifact. Store only
references and digests in it; never include credentials, tokens, private keys, or
customer data.

## Release verification

The approver supplies the external manifest path and its independently approved
digest:

```powershell
.\scripts\verify-production-readiness-evidence.ps1 `
  -EvidencePath D:\approved-evidence\production-readiness.json `
  -ExpectedSha256 <64-character-approved-sha256>
```

A successful command emits a receipt containing `ready: true`, manifest ID,
change reference, gate count, verification time, and manifest digest. Archive
that receipt in the external change record. A missing, repository-local,
incomplete, duplicate, expired, placeholder, secret-shaped, or digest-mismatched
manifest fails closed.

The verifier establishes structure, freshness, and integrity relative to the
approved digest. Human control owners remain accountable for the truth and legal
sufficiency of referenced evidence. Provider activation must also complete the
separate lifecycle in `production-provider-activation-runbook.md`; neither gate
substitutes for the other. Issue #56 remains open until real external evidence is
supplied and independently reviewed.