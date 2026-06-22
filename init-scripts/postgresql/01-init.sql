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
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_account_type   CHECK (account_type IN ('CHECKING', 'SAVINGS', 'INVESTMENT')),
    CONSTRAINT chk_currency       CHECK (currency     IN ('TRY', 'USD', 'EUR', 'GBP')),
    CONSTRAINT chk_account_status CHECK (status       IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_balance_positive CHECK (balance    >= 0)
);

-- Account tablosu indexleri
CREATE INDEX idx_accounts_user_id        ON account_service.accounts (user_id);
CREATE INDEX idx_accounts_account_number ON account_service.accounts (account_number);
CREATE INDEX idx_accounts_status         ON account_service.accounts (status);

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
    idempotency_key     VARCHAR(100)  UNIQUE,
    error_message       VARCHAR(500),
    metadata            JSONB,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,

    CONSTRAINT chk_tx_type   CHECK (type   IN ('TRANSFER', 'PAYMENT', 'DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT chk_tx_status CHECK (status IN ('PENDING', 'VALIDATED', 'CHECKED', 'PROCESSING', 'PROCESSED', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_fraud_score     CHECK (fraud_score BETWEEN 0 AND 100)
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
    transaction_id    UUID         NOT NULL,
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
