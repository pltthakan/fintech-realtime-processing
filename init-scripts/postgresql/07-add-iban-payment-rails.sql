-- IBAN-based Havale/EFT/FAST orchestration for existing databases.

CREATE SCHEMA IF NOT EXISTS payment_rail_service;

ALTER TABLE account_service.accounts
    ADD COLUMN IF NOT EXISTS reserved_balance NUMERIC(15,2) NOT NULL DEFAULT 0.00;

ALTER TABLE account_service.accounts DROP CONSTRAINT IF EXISTS chk_reserved_balance;
ALTER TABLE account_service.accounts
    ADD CONSTRAINT chk_reserved_balance CHECK (reserved_balance >= 0 AND reserved_balance <= balance);

-- Eski sürüm sabit TR33 kullandığı için checksum'ı hatalı üretilmiş hesapları
-- aynı BBAN'ı koruyarak ISO 13616 MOD-97 kontrol haneleriyle düzelt.
UPDATE account_service.accounts
SET account_number = 'TR'
    || LPAD((98 - MOD((SUBSTRING(account_number FROM 5) || '292700')::NUMERIC, 97))::TEXT, 2, '0')
    || SUBSTRING(account_number FROM 5)
WHERE account_number ~ '^TR[0-9]{24}$'
  AND MOD((SUBSTRING(account_number FROM 5) || '2927' || SUBSTRING(account_number FROM 3 FOR 2))::NUMERIC, 97) <> 1;

CREATE TABLE IF NOT EXISTS account_service.fund_reservations (
    transaction_id    UUID PRIMARY KEY,
    account_id        BIGINT NOT NULL REFERENCES account_service.accounts(id),
    amount            NUMERIC(15,2) NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    daily_spent_date  DATE NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reservation_amount CHECK (amount > 0),
    CONSTRAINT chk_reservation_currency CHECK (currency IN ('TRY', 'USD', 'EUR', 'GBP')),
    CONSTRAINT chk_reservation_status CHECK (status IN ('RESERVED', 'SETTLED', 'RELEASED'))
);

CREATE INDEX IF NOT EXISTS idx_fund_reservations_account
    ON account_service.fund_reservations (account_id, status);

ALTER TABLE transaction_service.transactions
    ADD COLUMN IF NOT EXISTS beneficiary_iban VARCHAR(34),
    ADD COLUMN IF NOT EXISTS beneficiary_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS beneficiary_bank_code VARCHAR(10),
    ADD COLUMN IF NOT EXISTS transfer_rail VARCHAR(20),
    ADD COLUMN IF NOT EXISTS external_reference VARCHAR(80);

ALTER TABLE transaction_service.transactions DROP CONSTRAINT IF EXISTS chk_transfer_rail;
ALTER TABLE transaction_service.transactions
    ADD CONSTRAINT chk_transfer_rail CHECK (
        transfer_rail IS NULL OR transfer_rail IN ('INTERNAL', 'HAVALE', 'EFT', 'FAST')
    );

ALTER TABLE transaction_service.transactions DROP CONSTRAINT IF EXISTS chk_tx_account_shape;
ALTER TABLE transaction_service.transactions
    ADD CONSTRAINT chk_tx_account_shape CHECK (
        (type = 'TRANSFER' AND source_account_id IS NOT NULL AND (
            (target_account_id IS NOT NULL AND source_account_id <> target_account_id) OR
            (target_account_id IS NULL AND beneficiary_iban IS NOT NULL AND transfer_rail IN ('EFT', 'FAST'))
        )) OR
        (type IN ('PAYMENT', 'WITHDRAWAL') AND source_account_id IS NOT NULL AND target_account_id IS NULL) OR
        (type = 'DEPOSIT' AND source_account_id IS NULL AND target_account_id IS NOT NULL)
    ) NOT VALID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_external_reference
    ON transaction_service.transactions (external_reference)
    WHERE external_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tx_beneficiary_iban
    ON transaction_service.transactions (beneficiary_iban);

CREATE TABLE IF NOT EXISTS payment_rail_service.payment_rail_attempts (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id          UUID NOT NULL UNIQUE,
    external_reference      VARCHAR(80) NOT NULL UNIQUE,
    rail                    VARCHAR(20) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    beneficiary_iban_hash   VARCHAR(64) NOT NULL,
    beneficiary_iban_masked VARCHAR(40) NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    failure_reason          VARCHAR(255),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_rail CHECK (rail IN ('EFT', 'FAST')),
    CONSTRAINT chk_payment_rail_status CHECK (status IN ('SETTLED', 'FAILED')),
    CONSTRAINT chk_payment_rail_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_rail_currency CHECK (currency = 'TRY')
);

CREATE TABLE IF NOT EXISTS payment_rail_service.outbox_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_id    VARCHAR(100) NOT NULL,
    topic           VARCHAR(150) NOT NULL,
    event_key       VARCHAR(150) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT chk_payment_rail_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX IF NOT EXISTS idx_payment_rail_outbox_pending
    ON payment_rail_service.outbox_events (status, created_at);
