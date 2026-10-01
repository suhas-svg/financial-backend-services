# Beneficiary Management and Trusted Recipients Design

## Goal

Build a customer-owned recipient system so customers can save trusted transfer destinations, maintain them over time, and reuse them in immediate and scheduled transfer creation.

## Scope

The MVP includes recipient create, list, edit, detail, and disable flows. It integrates saved recipients into the customer Move Money transfer form and Scheduled Transfers create form. It does not add approvals, multi-factor confirmation, external bank rails, recipient verification deposits, admin recipient management, or transaction-service recipient ownership.

## Architecture

`account-service` owns beneficiary records because they are customer-profile-adjacent data. `transaction-service` keeps executing transfers and scheduled transfers using account IDs, and remains responsible for source ownership, debitability, limits, idempotency, ledger posting, and execution failures.

The frontend resolves a selected recipient to the recipient's `destinationAccountId` before calling existing transaction APIs. This keeps the transaction-service API stable while still giving customers a saved-recipient workflow.

## Backend Design

Add a `beneficiaries` table in `account-service` with:

- `beneficiary_id`: UUID string primary key.
- `user_id`: authenticated customer username.
- `display_name`: required customer-facing name.
- `destination_account_id`: required existing account ID.
- `currency`: required `USD`, `EUR`, or `GBP`.
- `nickname`: optional short label.
- `notes`: optional customer notes.
- `status`: `ACTIVE` or `DISABLED`.
- `created_at`, `updated_at`, `disabled_at`, and `version`.

Customer APIs:

- `POST /api/beneficiaries`
- `GET /api/beneficiaries?status=ACTIVE|DISABLED|`
- `GET /api/beneficiaries/{beneficiaryId}`
- `PUT /api/beneficiaries/{beneficiaryId}`
- `DELETE /api/beneficiaries/{beneficiaryId}`

`DELETE` is a soft disable. Disabled recipients remain addressable by direct detail lookup for history, but normal selector queries request `ACTIVE`.

Validation rules:

- The authenticated username is always the beneficiary owner.
- Destination account must exist in `account-service`.
- Destination account currency must match the beneficiary currency.
- The destination account must not be owned by the same authenticated customer. This keeps recipients external or other-customer-owned for the MVP, while the existing account selectors continue supporting own-account transfers.
- Active duplicate recipients for the same `userId`, `destinationAccountId`, and `currency` are rejected.
- Disabled recipients cannot be edited except by re-creating a new active recipient.

## Frontend Design

Add customer route `/beneficiaries` and sidebar label `Recipients`.

Create `BeneficiariesPage` with:

- Summary header with active recipient count.
- Create/edit form for display name, destination account ID, currency, nickname, and notes.
- Recipient table/list with selected recipient detail.
- Disable action for active recipients.
- Empty and error states using existing UI primitives.

Move Money integration:

- In the transfer panel, show a destination mode selector with saved recipient as the primary path and manual account ID fallback.
- Selecting a recipient populates `toAccountId` and currency.
- Manual mode preserves the existing destination account behavior.

Scheduled Transfers integration:

- In the create schedule form, use the same saved-recipient/manual destination pattern.
- The submitted payload remains the existing `ScheduledTransferValues` shape with `toAccountId`.

## Testing

Backend tests:

- Service creates a beneficiary with normalized fields after validating destination account existence, ownership, and currency.
- Service rejects same-owner destination accounts.
- Service rejects duplicate active recipients.
- Service lists only the authenticated user's recipients and filters by status.
- Service updates display fields for owned active recipients.
- Service soft-disables owned recipients.
- Controller delegates using `Authentication.getName()` and denies cross-user access through service ownership checks.

Frontend tests:

- API helpers map beneficiary calls to `/account-api/api/beneficiaries`.
- Form schema validates required display name, destination account ID, currency, and length limits.
- Customer navigation exposes `Recipients`.
- Recipients page lists, creates, edits, and disables recipients.
- Move Money recipient selection submits the selected recipient destination account ID.
- Scheduled Transfers recipient selection submits the selected recipient destination account ID.

## Verification

Run focused account-service tests, focused frontend tests, and a frontend production build. Run transaction-service scheduled transfer tests only if transaction-service contracts are changed; the recommended design avoids those changes.
