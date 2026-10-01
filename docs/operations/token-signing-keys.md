# Token signing keys

No secret is shared between the services. Each token is signed with a private key that only
its issuer holds, and verified with a public key the issuer publishes as a JWK Set
([RFC 7517](https://www.rfc-editor.org/rfc/rfc7517)).

| Token | Signed by | Verified by | Public keys at |
| --- | --- | --- | --- |
| User access token (15 min) | account-service, RS256 | both services | account-service `/.well-known/jwks.json` |
| Internal service token (60 s) | transaction-service, RS256 | account-service | transaction-service `/.well-known/jwks.json` |

Only RS256 is accepted. An HS256 token is rejected even if someone signs it with the published
public key as the "secret" (the classic algorithm-confusion attack); tests cover that case.
Refresh tokens are opaque, stored hashed in account-service, and are unaffected.

## Configuration

| Service | Variable | Purpose |
| --- | --- | --- |
| account-service | `JWT_SIGNING_PRIVATE_KEY` | PKCS#8 PEM private key for user tokens |
| | `JWT_SIGNING_KEY_ID` | `kid` for that key (defaults to a thumbprint of the key) |
| | `JWT_SIGNING_PREVIOUS_PUBLIC_KEY`, `JWT_SIGNING_PREVIOUS_KEY_ID` | still-published key during a rotation |
| | `JWT_ALLOW_GENERATED_KEY` | `false` in shared environments |
| | `INTERNAL_JWKS_URI` | transaction-service's JWKS |
| transaction-service | `JWT_INTERNAL_SIGNING_PRIVATE_KEY` (+ `_KEY_ID`, `_PREVIOUS_*`) | private key for internal tokens |
| | `JWT_INTERNAL_ALLOW_GENERATED_KEY` | `false` in shared environments |
| | `JWT_JWKS_URI` | account-service's JWKS (defaults to `ACCOUNT_SERVICE_URL` + `/.well-known/jwks.json`) |

PEM values may contain real newlines or `\n` escapes, so they fit in a single-line secret.

Without a configured key a service generates one at startup. That is fine for a single local
process or the synthetic sandbox, but each restart invalidates outstanding access tokens
(browsers renew silently through the refresh cookie), and replicas would each sign with a
different key. The production Helm values set both `*_ALLOW_GENERATED_KEY=false`, so a
misconfigured deployment fails to start rather than issuing tokens no other replica accepts.

## Creating a key

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out user-token-key.pem
openssl pkey -in user-token-key.pem -pubout -out user-token-key.pub.pem
```

Store the private key in your secret manager (or KMS-backed secret store) and inject it into the
service's Kubernetes secret. Never commit it; the full-history secret scan will flag it.

## Rotating a key

1. Generate a new key pair and pick a new `kid` (for example a date: `user-2026-10`).
2. Deploy the issuer with the new key as `*_SIGNING_PRIVATE_KEY` / `*_KEY_ID`, and the **old public
   key** as `*_PREVIOUS_PUBLIC_KEY` / `*_PREVIOUS_KEY_ID`. Both keys are now published; new tokens
   use the new key.
3. Verifiers pick the new key up on their own: an unknown `kid` makes them refetch the JWKS
   (at most every 10 s), and the cache refreshes every 5 minutes anyway.
4. After the longest token lifetime has passed (15 minutes for user tokens, 60 s for internal
   tokens), remove the `*_PREVIOUS_*` values and redeploy.

To revoke a compromised key, skip step 2's "previous" key: every token it signed stops verifying
at once, and users sign in again through their refresh session.

## Next step: managed keys

Keys are loaded from a secret today. Moving signing into a KMS or HSM (the private key never
leaves it) or handing user login to an external identity provider is the remaining step for the
`IDP_KMS` readiness gate; the JWKS-based verification here works unchanged with either.
