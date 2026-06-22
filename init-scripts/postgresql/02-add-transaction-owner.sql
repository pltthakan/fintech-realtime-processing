-- Transaction ownership migration.
-- Existing records are associated with the owner of their source account.

BEGIN;

ALTER TABLE transaction_service.transactions
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

UPDATE transaction_service.transactions transaction
SET user_id = account.user_id
FROM account_service.accounts account
WHERE transaction.source_account_id = account.id
  AND transaction.user_id IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM transaction_service.transactions
        WHERE user_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot migrate transaction ownership: transactions without a source account remain';
    END IF;
END $$;

ALTER TABLE transaction_service.transactions
    ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tx_user_id
    ON transaction_service.transactions (user_id);

COMMIT;
