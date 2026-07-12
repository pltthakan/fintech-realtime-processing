-- Existing PostgreSQL volumes için transactional outbox, consumer inbox ve
-- transaction status enum/constraint uyumluluğu.

BEGIN;

ALTER TABLE transaction_service.transactions
    DROP CONSTRAINT IF EXISTS chk_tx_status;

ALTER TABLE transaction_service.transactions
    ADD CONSTRAINT chk_tx_status CHECK (status IN (
        'PENDING', 'VALIDATED', 'FRAUD_CHECK', 'CHECKED', 'BLOCKED',
        'PROCESSING', 'PROCESSED', 'COMPLETED', 'FAILED', 'CANCELLED'
    ));

CREATE TABLE IF NOT EXISTS account_service.processed_events (
    consumer_name       VARCHAR(100) NOT NULL,
    event_id            VARCHAR(100) NOT NULL,
    processed_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE IF NOT EXISTS account_service.outbox_events (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_id        VARCHAR(100) NOT NULL,
    topic               VARCHAR(150) NOT NULL,
    event_key           VARCHAR(150) NOT NULL,
    payload             TEXT         NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts            INTEGER      NOT NULL DEFAULT 0,
    last_error          VARCHAR(1000),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CONSTRAINT chk_account_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX IF NOT EXISTS idx_account_outbox_pending
    ON account_service.outbox_events (status, created_at);

CREATE TABLE IF NOT EXISTS transaction_service.outbox_events (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_id        VARCHAR(100) NOT NULL,
    topic               VARCHAR(150) NOT NULL,
    event_key           VARCHAR(150) NOT NULL,
    payload             TEXT         NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts            INTEGER      NOT NULL DEFAULT 0,
    last_error          VARCHAR(1000),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CONSTRAINT chk_transaction_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX IF NOT EXISTS idx_transaction_outbox_pending
    ON transaction_service.outbox_events (status, created_at);

COMMIT;
