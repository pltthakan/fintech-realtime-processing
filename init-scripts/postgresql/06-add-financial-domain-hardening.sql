-- P0/P1/P2 financial domain hardening for existing databases.

ALTER TABLE account_service.accounts
    ADD COLUMN IF NOT EXISTS daily_spent NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS daily_spent_date DATE;

ALTER TABLE account_service.accounts DROP CONSTRAINT IF EXISTS chk_daily_limit_positive;
ALTER TABLE account_service.accounts
    ADD CONSTRAINT chk_daily_limit_positive CHECK (daily_limit >= 0);
ALTER TABLE account_service.accounts DROP CONSTRAINT IF EXISTS chk_daily_spent_positive;
ALTER TABLE account_service.accounts
    ADD CONSTRAINT chk_daily_spent_positive CHECK (daily_spent >= 0);

UPDATE transaction_service.transactions
SET idempotency_key = 'legacy-' || id::text
WHERE idempotency_key IS NULL OR btrim(idempotency_key) = '';

ALTER TABLE transaction_service.transactions
    ALTER COLUMN idempotency_key SET NOT NULL,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE transaction_service.transactions DROP CONSTRAINT IF EXISTS chk_tx_account_shape;
ALTER TABLE transaction_service.transactions
    ADD CONSTRAINT chk_tx_account_shape CHECK (
        (type = 'TRANSFER' AND source_account_id IS NOT NULL AND target_account_id IS NOT NULL AND source_account_id <> target_account_id) OR
        (type IN ('PAYMENT', 'WITHDRAWAL') AND source_account_id IS NOT NULL AND target_account_id IS NULL) OR
        (type = 'DEPOSIT' AND source_account_id IS NULL AND target_account_id IS NOT NULL)
    ) NOT VALID;

CREATE TABLE IF NOT EXISTS account_service.ledger_transactions (
    id                  UUID PRIMARY KEY,
    reference_number    VARCHAR(100) NOT NULL UNIQUE,
    transaction_type    VARCHAR(20)  NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    total_amount        NUMERIC(15,2) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'POSTED',
    posted_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_ledger_tx_type CHECK (transaction_type IN ('TRANSFER', 'PAYMENT', 'DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT chk_ledger_tx_currency CHECK (currency IN ('TRY', 'USD', 'EUR', 'GBP')),
    CONSTRAINT chk_ledger_tx_amount CHECK (total_amount > 0),
    CONSTRAINT chk_ledger_tx_status CHECK (status = 'POSTED')
);

CREATE TABLE IF NOT EXISTS account_service.ledger_entries (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ledger_transaction_id   UUID NOT NULL REFERENCES account_service.ledger_transactions(id),
    account_id              BIGINT REFERENCES account_service.accounts(id),
    account_code            VARCHAR(100) NOT NULL,
    direction               VARCHAR(10) NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    balance_after           NUMERIC(15,2),
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ledger_entry_side UNIQUE (ledger_transaction_id, account_code, direction),
    CONSTRAINT chk_ledger_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_entry_currency CHECK (currency IN ('TRY', 'USD', 'EUR', 'GBP')),
    CONSTRAINT chk_ledger_entry_amount CHECK (amount > 0),
    CONSTRAINT chk_ledger_account_reference CHECK (
        (account_id IS NOT NULL AND account_code LIKE 'ACCOUNT:%') OR
        (account_id IS NULL AND account_code LIKE 'SYSTEM:%')
    )
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_account
    ON account_service.ledger_entries (account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_transaction
    ON account_service.ledger_entries (ledger_transaction_id);

CREATE OR REPLACE FUNCTION account_service.prevent_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger records are immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ledger_transactions_immutable ON account_service.ledger_transactions;
CREATE TRIGGER trg_ledger_transactions_immutable
    BEFORE UPDATE OR DELETE ON account_service.ledger_transactions
    FOR EACH ROW EXECUTE FUNCTION account_service.prevent_ledger_mutation();

DROP TRIGGER IF EXISTS trg_ledger_entries_immutable ON account_service.ledger_entries;
CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON account_service.ledger_entries
    FOR EACH ROW EXECUTE FUNCTION account_service.prevent_ledger_mutation();

-- Migration öncesi bakiyeleri bir defaya mahsus dengeli açılış journal'larıyla ledger'a al.
DO $$
DECLARE
    account_row RECORD;
    ledger_id UUID;
BEGIN
    FOR account_row IN
        SELECT a.*
        FROM account_service.accounts a
        WHERE a.balance <> 0
          AND NOT EXISTS (
              SELECT 1 FROM account_service.ledger_entries le WHERE le.account_id = a.id
          )
    LOOP
        ledger_id := uuid_generate_v4();
        INSERT INTO account_service.ledger_transactions
            (id, reference_number, transaction_type, currency, total_amount, status, posted_at)
        VALUES
            (ledger_id, 'MIGRATION-OPENING-' || account_row.id, 'DEPOSIT', account_row.currency,
             account_row.balance, 'POSTED', NOW());
        INSERT INTO account_service.ledger_entries
            (ledger_transaction_id, account_id, account_code, direction, amount, currency, balance_after, created_at)
        VALUES
            (ledger_id, NULL, 'SYSTEM:OPENING_BALANCE', 'DEBIT', account_row.balance,
             account_row.currency, NULL, NOW()),
            (ledger_id, account_row.id, 'ACCOUNT:' || account_row.id, 'CREDIT', account_row.balance,
             account_row.currency, account_row.balance, NOW());
    END LOOP;
END;
$$;

-- Retry edilen fraud event'i için ikinci sonuç kaydı oluşmasını engelle.
DELETE FROM fraud_service.fraud_check_results duplicate
USING fraud_service.fraud_check_results original
WHERE duplicate.transaction_id = original.transaction_id
  AND duplicate.id > original.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_fraud_results_transaction
    ON fraud_service.fraud_check_results (transaction_id);
