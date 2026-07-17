-- ============================================
-- FINTECH REALTIME PROCESSING SYSTEM
-- PostgreSQL Initialization Script
-- ============================================

-- Servis bazlı schema'lar oluştur (her mikro servisin kendi schema'sı)
CREATE SCHEMA IF NOT EXISTS user_service;
CREATE SCHEMA IF NOT EXISTS account_service;
CREATE SCHEMA IF NOT EXISTS transaction_service;
CREATE SCHEMA IF NOT EXISTS fraud_service;
CREATE SCHEMA IF NOT EXISTS audit_service;

-- UUID extension'ı etkinleştir
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- Şifreleme extension'ı
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================
-- USER SERVICE TABLOLARI
-- ============================================

CREATE TABLE user_service.users (
    id                BIGSERIAL PRIMARY KEY,
    username          VARCHAR(50)  NOT NULL UNIQUE,
    email             VARCHAR(100) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    first_name        VARCHAR(50),
    last_name         VARCHAR(50),
    phone_number      VARCHAR(20),
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_user_role   CHECK (role   IN ('USER', 'ADMIN', 'ANALYST')),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'))
);

-- User tablosu indexleri
CREATE INDEX idx_users_email    ON user_service.users (email);
CREATE INDEX idx_users_username ON user_service.users (username);
CREATE INDEX idx_users_status   ON user_service.users (status);

-- Refresh token'ın kendisi saklanmaz; yalnızca SHA-256 hash'i tutulur.
-- family_id rotation/reuse detection ile aynı oturum ailesini topluca iptal eder.
CREATE TABLE user_service.refresh_tokens (
    id                      UUID PRIMARY KEY,
    user_id                 BIGINT       NOT NULL REFERENCES user_service.users(id) ON DELETE CASCADE,
    token_hash              VARCHAR(64)  NOT NULL UNIQUE,
    family_id               UUID         NOT NULL,
    expires_at              TIMESTAMPTZ  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at            TIMESTAMPTZ,
    revoked_at              TIMESTAMPTZ,
    replaced_by_token_id    UUID,
    revoked_reason          VARCHAR(50),
    version                 BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_refresh_tokens_user
    ON user_service.refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_active
    ON user_service.refresh_tokens (family_id, revoked_at);
CREATE INDEX idx_refresh_tokens_expiry
    ON user_service.refresh_tokens (expires_at);

-- ============================================
-- ACCOUNT SERVICE TABLOLARI
-- ============================================

CREATE TABLE account_service.accounts (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    account_number    VARCHAR(26)  NOT NULL UNIQUE,
    account_name      VARCHAR(100),
    account_type      VARCHAR(20)  NOT NULL DEFAULT 'CHECKING',
    currency          VARCHAR(3)   NOT NULL DEFAULT 'TRY',
    balance           NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    daily_limit       NUMERIC(15,2) NOT NULL DEFAULT 50000.00,
    daily_spent       NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    daily_spent_date  DATE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_account_type   CHECK (account_type IN ('CHECKING', 'SAVINGS', 'INVESTMENT')),
    CONSTRAINT chk_currency       CHECK (currency     IN ('TRY', 'USD', 'EUR', 'GBP')),
    CONSTRAINT chk_account_status CHECK (status       IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_balance_positive CHECK (balance    >= 0),
    CONSTRAINT chk_daily_limit_positive CHECK (daily_limit >= 0),
    CONSTRAINT chk_daily_spent_positive CHECK (daily_spent >= 0)
);

-- Account tablosu indexleri
CREATE INDEX idx_accounts_user_id        ON account_service.accounts (user_id);
CREATE INDEX idx_accounts_account_number ON account_service.accounts (account_number);
CREATE INDEX idx_accounts_status         ON account_service.accounts (status);

-- Kafka consumer inbox: aynı transaction event'inin bakiyeyi ikinci kez
-- değiştirmesini engeller. Kayıt, bakiye güncellemesiyle aynı DB transaction'ında yazılır.
CREATE TABLE account_service.processed_events (
    consumer_name       VARCHAR(100) NOT NULL,
    event_id            VARCHAR(100) NOT NULL,
    processed_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer_name, event_id)
);

-- Account DB değişikliği ile Kafka publish isteğini atomik hale getiren outbox.
CREATE TABLE account_service.outbox_events (
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

CREATE INDEX idx_account_outbox_pending
    ON account_service.outbox_events (status, created_at);

-- Bakiye tablosundan bağımsız, değiştirilemez çift taraflı muhasebe defteri.
CREATE TABLE account_service.ledger_transactions (
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

CREATE TABLE account_service.ledger_entries (
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

CREATE INDEX idx_ledger_entries_account
    ON account_service.ledger_entries (account_id, created_at DESC);
CREATE INDEX idx_ledger_entries_transaction
    ON account_service.ledger_entries (ledger_transaction_id);

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

-- ============================================
-- TRANSACTION SERVICE TABLOLARI
-- ============================================

CREATE TABLE transaction_service.transactions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             BIGINT       NOT NULL,
    source_account_id   BIGINT,
    target_account_id   BIGINT,
    amount              NUMERIC(15,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL DEFAULT 'TRY',
    type                VARCHAR(20)   NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    fraud_score         SMALLINT      DEFAULT 0,
    description         VARCHAR(255),
    reference_number    VARCHAR(50)   UNIQUE,
    idempotency_key     VARCHAR(100)  NOT NULL UNIQUE,
    error_message       VARCHAR(500),
    metadata            JSONB,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT chk_tx_type   CHECK (type   IN ('TRANSFER', 'PAYMENT', 'DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT chk_tx_status CHECK (status IN ('PENDING', 'VALIDATED', 'FRAUD_CHECK', 'CHECKED', 'BLOCKED', 'PROCESSING', 'PROCESSED', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_fraud_score     CHECK (fraud_score BETWEEN 0 AND 100),
    CONSTRAINT chk_tx_account_shape CHECK (
        (type = 'TRANSFER' AND source_account_id IS NOT NULL AND target_account_id IS NOT NULL AND source_account_id <> target_account_id) OR
        (type IN ('PAYMENT', 'WITHDRAWAL') AND source_account_id IS NOT NULL AND target_account_id IS NULL) OR
        (type = 'DEPOSIT' AND source_account_id IS NULL AND target_account_id IS NOT NULL)
    )
);

-- Transaction tablosu indexleri
CREATE INDEX idx_tx_source_account ON transaction_service.transactions (source_account_id);
CREATE INDEX idx_tx_target_account ON transaction_service.transactions (target_account_id);
CREATE INDEX idx_tx_user_id        ON transaction_service.transactions (user_id);
CREATE INDEX idx_tx_status         ON transaction_service.transactions (status);
CREATE INDEX idx_tx_type           ON transaction_service.transactions (type);
CREATE INDEX idx_tx_created_at     ON transaction_service.transactions (created_at DESC);
CREATE INDEX idx_tx_idempotency    ON transaction_service.transactions (idempotency_key);

-- Transaction durum geçmişi tablosu (her aşamayı kaydet)
CREATE TABLE transaction_service.transaction_status_history (
    id                BIGSERIAL PRIMARY KEY,
    transaction_id    UUID         NOT NULL REFERENCES transaction_service.transactions(id),
    previous_status   VARCHAR(30),
    new_status        VARCHAR(30)  NOT NULL,
    service_name      VARCHAR(50)  NOT NULL,
    message           VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tx_history_tx_id ON transaction_service.transaction_status_history (transaction_id);

-- Transaction kaydı ile ilk Kafka event'ini atomik hale getiren outbox.
CREATE TABLE transaction_service.outbox_events (
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

CREATE INDEX idx_transaction_outbox_pending
    ON transaction_service.outbox_events (status, created_at);

-- ============================================
-- AUDIT SERVICE TABLOLARI
-- ============================================

-- Kullanıcının hangi veriyi ne zaman görüntülediğini veya değiştirdiğini kaydeder.
CREATE TABLE audit_service.audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    actor_user_id   BIGINT       NOT NULL,
    actor_username  VARCHAR(50)  NOT NULL,
    actor_role      VARCHAR(20)  NOT NULL,
    action          VARCHAR(50)  NOT NULL,
    resource_type   VARCHAR(30)  NOT NULL,
    resource_id     VARCHAR(100) NOT NULL,
    service_name    VARCHAR(50)  NOT NULL,
    http_method     VARCHAR(10)  NOT NULL,
    client_ip       VARCHAR(64),
    details         VARCHAR(1000),
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_occurred_at ON audit_service.audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_logs_actor       ON audit_service.audit_logs (actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_resource    ON audit_service.audit_logs (resource_type, resource_id, occurred_at DESC);

-- ============================================
-- FRAUD SERVICE TABLOLARI
-- ============================================

CREATE TABLE fraud_service.fraud_rules (
    id                BIGSERIAL PRIMARY KEY,
    rule_name         VARCHAR(100) NOT NULL UNIQUE,
    rule_type         VARCHAR(50)  NOT NULL,
    description       VARCHAR(500),
    condition_json    JSONB        NOT NULL,
    risk_weight       SMALLINT     NOT NULL DEFAULT 10,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_rule_type CHECK (rule_type IN ('VELOCITY', 'AMOUNT', 'BLACKLIST', 'PATTERN', 'GEO')),
    CONSTRAINT chk_risk_weight CHECK (risk_weight BETWEEN 1 AND 100)
);

CREATE TABLE fraud_service.blacklist (
    id                BIGSERIAL PRIMARY KEY,
    entity_type       VARCHAR(20)  NOT NULL,
    entity_value      VARCHAR(255) NOT NULL,
    reason            VARCHAR(500),
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_entity_type CHECK (entity_type IN ('ACCOUNT', 'USER', 'IP', 'IBAN'))
);

CREATE INDEX idx_blacklist_entity ON fraud_service.blacklist (entity_type, entity_value);
CREATE INDEX idx_blacklist_active ON fraud_service.blacklist (is_active);

CREATE TABLE fraud_service.fraud_check_results (
    id                BIGSERIAL PRIMARY KEY,
    transaction_id    UUID         NOT NULL UNIQUE,
    total_risk_score  SMALLINT     NOT NULL,
    is_suspicious     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_blocked        BOOLEAN      NOT NULL DEFAULT FALSE,
    matched_rules     JSONB,
    check_duration_ms INTEGER,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraud_results_tx_id ON fraud_service.fraud_check_results (transaction_id);

-- ============================================
-- BAŞLANGIÇ VERİLERİ (SEED DATA)
-- ============================================

-- Varsayılan fraud kuralları
INSERT INTO fraud_service.fraud_rules (rule_name, rule_type, description, condition_json, risk_weight) VALUES
('HIGH_AMOUNT_SINGLE', 'AMOUNT', 'Tek seferde 100.000 TL üzeri işlem', '{"max_amount": 100000, "currency": "TRY"}', 30),
('VELOCITY_5MIN', 'VELOCITY', '5 dakika içinde 3 den fazla işlem', '{"time_window_minutes": 5, "max_count": 3}', 25),
('VELOCITY_1HOUR', 'VELOCITY', '1 saat içinde 10 dan fazla işlem', '{"time_window_minutes": 60, "max_count": 10}', 20),
('DAILY_LIMIT_EXCEEDED', 'AMOUNT', 'Günlük limit aşımı', '{"daily_max_amount": 250000, "currency": "TRY"}', 40),
('NIGHT_TRANSACTION', 'PATTERN', 'Gece 00:00-06:00 arası yüksek tutarlı işlem', '{"start_hour": 0, "end_hour": 6, "min_amount": 10000}', 15),
('NEW_ACCOUNT_HIGH_AMOUNT', 'PATTERN', 'Yeni hesaptan (7 gün) yüksek tutarlı işlem', '{"account_age_days": 7, "min_amount": 5000}', 35);

-- Test kullanıcıları
INSERT INTO user_service.users (username, email, password_hash, first_name, last_name, role, status, email_verified) VALUES
('admin', 'admin@fintech.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'System', 'Admin', 'ADMIN', 'ACTIVE', true),
('analyst', 'analyst@fintech.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Data', 'Analyst', 'ANALYST', 'ACTIVE', true),
('testuser1', 'test1@fintech.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Ahmet', 'Yılmaz', 'USER', 'ACTIVE', true),
('testuser2', 'test2@fintech.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Ayşe', 'Demir', 'USER', 'ACTIVE', true);

-- Test hesapları
INSERT INTO account_service.accounts (user_id, account_number, account_name, account_type, currency, balance, daily_limit) VALUES
(3, 'TR330006100519786457841326', 'Vadesiz TL Hesabı',  'CHECKING', 'TRY', 25000.00, 50000.00),
(3, 'TR330006100519786457841327', 'Dolar Hesabı',       'CHECKING', 'USD', 5000.00,  10000.00),
(4, 'TR330006100519786457841328', 'Vadesiz TL Hesabı',  'CHECKING', 'TRY', 18000.00, 50000.00),
(4, 'TR330006100519786457841329', 'Birikim Hesabı',     'SAVINGS',  'TRY', 75000.00, 25000.00);

-- Demo/seed bakiyeleri de muhasebe defterinde dengeli bir açılış journal'ına sahiptir.
DO $$
DECLARE
    account_row RECORD;
    ledger_id UUID;
BEGIN
    FOR account_row IN
        SELECT * FROM account_service.accounts WHERE balance > 0
    LOOP
        ledger_id := uuid_generate_v4();
        INSERT INTO account_service.ledger_transactions
            (id, reference_number, transaction_type, currency, total_amount, status, posted_at)
        VALUES
            (ledger_id, 'SEED-OPENING-' || account_row.id, 'DEPOSIT', account_row.currency,
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

-- ============================================
-- UPDATED_AT TRIGGER FONKSİYONU
-- ============================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- updated_at trigger'larını tüm tablolara uygula
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON user_service.users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_accounts_updated_at
    BEFORE UPDATE ON account_service.accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transaction_service.transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_fraud_rules_updated_at
    BEFORE UPDATE ON fraud_service.fraud_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- TAMAMLANDI
-- ============================================
SELECT 'PostgreSQL initialization completed successfully!' AS status;
