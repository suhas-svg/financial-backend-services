# API reference

| Service | Spec | Gateway prefix |
| --- | --- | --- |
| account-service | [`account-service.openapi.json`](account-service.openapi.json) | `/account-api` |
| transaction-service | [`transaction-service.openapi.json`](transaction-service.openapi.json) | `/transaction-api` |

Open either file in any OpenAPI viewer (for example <https://editor.swagger.io>), or run a
service with `API_DOCS_ENABLED=true` and browse `/swagger-ui.html`. The docs endpoints are off
by default, so they never ship enabled by accident.

## How the specs stay correct

- **Generated, not hand-written.** springdoc builds the spec from the controllers.
  `OpenApiSpecTest` in each service fails when the committed file differs from what the
  service serves. After an intended API change:

  ```bash
  cd account-service && ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true
  cd ../transaction-service && ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true
  cd ../frontend && npm run api:types
  ```

- **Frontend types.** `npm run api:types` generates `frontend/src/api/generated/*.ts` from these
  files. `src/types.ts` derives its enums and the error type from them, so a server-side change
  breaks the frontend build rather than the UI.
- **Breaking changes.** The `API Contract (OpenAPI)` CI job runs `oasdiff breaking` against the
  base branch's spec and fails on removed endpoints, removed response fields, newly required
  request fields, narrowed enums and similar. A deliberate break needs a new versioned path.

## Errors

Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details body,
served as `application/problem+json`:

```json
{
  "type": "urn:financial:problem:insufficient-funds",
  "title": "Insufficient Funds",
  "status": 400,
  "detail": "Insufficient funds for transaction",
  "instance": "/api/transactions/transfer",
  "error": "Insufficient Funds",
  "message": "Insufficient funds for transaction",
  "path": "/api/transactions/transfer",
  "timestamp": "2026-10-01T10:15:30Z"
}
```

Branch on `type` (stable per kind of problem) and show `detail`. `error`, `message`, `path` and
`timestamp` repeat the same information for clients written before the RFC format. Validation
failures add `validationErrors` (field → message). A reversal conflict adds `transactionId`.
Server errors never include internal exception text.
