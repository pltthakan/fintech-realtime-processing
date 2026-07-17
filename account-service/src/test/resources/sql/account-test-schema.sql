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
    daily_limit     NUMERIC(15,2) NOT NULL DEFAULT 50000.00,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_account_type CHECK (account_type IN ('CHECKING', 'SAVINGS', 'INVESTMENT')),
    CONSTRAINT chk_currency CHECK (currency IN ('TRY', 'USD', 'EUR', 'GBP')),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_balance_positive CHECK (balance >= 0)
);

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
