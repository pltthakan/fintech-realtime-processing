CREATE SCHEMA IF NOT EXISTS account_service;
CREATE SCHEMA IF NOT EXISTS audit_service;

CREATE TABLE audit_service.audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    actor_user_id   BIGINT        NOT NULL,
    actor_username  VARCHAR(50)   NOT NULL,
    actor_role      VARCHAR(20)   NOT NULL,
    action          VARCHAR(50)   NOT NULL,
    resource_type   VARCHAR(30)   NOT NULL,
    resource_id     VARCHAR(100)  NOT NULL,
    service_name    VARCHAR(50)   NOT NULL,
    http_method     VARCHAR(10)   NOT NULL,
    client_ip       VARCHAR(64),
    details         VARCHAR(1000),
    occurred_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE account_service.accounts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    account_number  VARCHAR(26)   NOT NULL UNIQUE,
    account_name    VARCHAR(100),
    account_type    VARCHAR(20)   NOT NULL DEFAULT 'CHECKING',
    currency        VARCHAR(3)    NOT NULL DEFAULT 'TRY',
    balance         NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    reserved_balance NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    daily_limit     NUMERIC(15,2) NOT NULL DEFAULT 50000.00,
    daily_spent     NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    daily_spent_date DATE,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_account_type CHECK (account_type IN ('CHECKING', 'SAVINGS', 'INVESTMENT')),
    CONSTRAINT chk_currency CHECK (currency IN ('TRY', 'USD', 'EUR', 'GBP')),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_balance_positive CHECK (balance >= 0),
    CONSTRAINT chk_reserved_balance CHECK (
        reserved_balance >= 0 AND reserved_balance <= balance
    )
);

CREATE TABLE account_service.fund_reservations (
    transaction_id    UUID PRIMARY KEY,
    account_id        BIGINT NOT NULL REFERENCES account_service.accounts(id),
    amount            NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    currency          VARCHAR(3) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    daily_spent_date  DATE NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_fund_reservation_status CHECK (status IN ('RESERVED', 'SETTLED', 'RELEASED'))
);

CREATE INDEX idx_fund_reservations_account_status
    ON account_service.fund_reservations (account_id, status);

CREATE TABLE account_service.processed_events (
    consumer_name VARCHAR(100) NOT NULL,
    event_id      VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE account_service.outbox_events (
    id            UUID          PRIMARY KEY,
    aggregate_id  VARCHAR(100)  NOT NULL,
    topic         VARCHAR(150)  NOT NULL,
    event_key     VARCHAR(150)  NOT NULL,
    payload       TEXT          NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempts      INTEGER       NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMPTZ,
    CONSTRAINT chk_account_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX idx_account_outbox_pending
    ON account_service.outbox_events (status, created_at);

CREATE TABLE account_service.ledger_transactions (
    id                  UUID PRIMARY KEY,
    reference_number    VARCHAR(100) NOT NULL UNIQUE,
    transaction_type    VARCHAR(20) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    total_amount        NUMERIC(15,2) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    posted_at           TIMESTAMPTZ NOT NULL
);

CREATE TABLE account_service.ledger_entries (
    id                      UUID PRIMARY KEY,
    ledger_transaction_id   UUID NOT NULL REFERENCES account_service.ledger_transactions(id),
    account_id              BIGINT REFERENCES account_service.accounts(id),
    account_code            VARCHAR(100) NOT NULL,
    direction               VARCHAR(10) NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    balance_after           NUMERIC(15,2),
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ledger_entry_side UNIQUE (ledger_transaction_id, account_code, direction)
);

CREATE INDEX idx_ledger_entries_account
    ON account_service.ledger_entries (account_id, created_at DESC);

CREATE OR REPLACE FUNCTION account_service.prevent_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger records are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_transactions_immutable
    BEFORE UPDATE OR DELETE ON account_service.ledger_transactions
    FOR EACH ROW EXECUTE FUNCTION account_service.prevent_ledger_mutation();
CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON account_service.ledger_entries
    FOR EACH ROW EXECUTE FUNCTION account_service.prevent_ledger_mutation();
