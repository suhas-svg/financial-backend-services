CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS request_fingerprint_status VARCHAR(32);

UPDATE transactions
SET request_fingerprint_status = 'CURRENT'
WHERE request_fingerprint IS NOT NULL
  AND request_fingerprint_status IS NULL;

UPDATE transactions
SET request_fingerprint = encode(digest(
        concat(
            'DEPOSIT|', btrim(created_by), '|', btrim(to_account_id), '|',
            trim(trailing '.' FROM trim(trailing '0' FROM amount::text)), '|',
            btrim(coalesce(description, '')), '|', btrim(coalesce(reference, ''))
        ),
        'sha256'), 'hex'),
    request_fingerprint_status = 'LEGACY_RECONSTRUCTED'
WHERE idempotency_key IS NOT NULL
  AND request_fingerprint IS NULL
  AND type = 'DEPOSIT';

UPDATE transactions
SET request_fingerprint = encode(digest(
        concat(
            'WITHDRAWAL|', btrim(created_by), '|', btrim(from_account_id), '|',
            trim(trailing '.' FROM trim(trailing '0' FROM amount::text)), '|',
            btrim(coalesce(description, '')), '|', btrim(coalesce(reference, ''))
        ),
        'sha256'), 'hex'),
    request_fingerprint_status = 'LEGACY_RECONSTRUCTED'
WHERE idempotency_key IS NOT NULL
  AND request_fingerprint IS NULL
  AND type = 'WITHDRAWAL';

UPDATE transactions
SET request_fingerprint = encode(digest(
        concat(
            'REVERSAL|', btrim(created_by), '|', btrim(coalesce(original_transaction_id, '')), '|',
            btrim(coalesce(reversal_reason, ''))
        ),
        'sha256'), 'hex'),
    request_fingerprint_status = 'LEGACY_RECONSTRUCTED'
WHERE idempotency_key IS NOT NULL
  AND request_fingerprint IS NULL
  AND type = 'REVERSAL'
  AND original_transaction_id IS NOT NULL;

UPDATE transactions
SET request_fingerprint = encode(digest(
        concat('LEGACY_AMBIGUOUS|', transaction_id),
        'sha256'), 'hex'),
    request_fingerprint_status = 'LEGACY_AMBIGUOUS'
WHERE idempotency_key IS NOT NULL
  AND request_fingerprint IS NULL;

UPDATE transactions
SET request_fingerprint_status = 'NOT_APPLICABLE'
WHERE idempotency_key IS NULL
  AND request_fingerprint IS NULL
  AND request_fingerprint_status IS NULL;

ALTER TABLE transactions
    ALTER COLUMN request_fingerprint_status SET DEFAULT 'CURRENT',
    ALTER COLUMN request_fingerprint_status SET NOT NULL;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transaction_request_fingerprint_status
        CHECK (request_fingerprint_status IN (
            'CURRENT',
            'LEGACY_RECONSTRUCTED',
            'LEGACY_AMBIGUOUS',
            'NOT_APPLICABLE'
        ));

ALTER TABLE transactions
    ADD CONSTRAINT chk_keyed_transaction_has_fingerprint
        CHECK (
            idempotency_key IS NULL
            OR (
                request_fingerprint IS NOT NULL
                AND request_fingerprint_status <> 'NOT_APPLICABLE'
            )
        );

COMMENT ON COLUMN transactions.request_fingerprint_status IS
    'CURRENT for new fingerprints, LEGACY_RECONSTRUCTED when V101 rebuilt the canonical payload, '
    'LEGACY_AMBIGUOUS when replay requires operator reconciliation, or NOT_APPLICABLE for old unkeyed rows';
