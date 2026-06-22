-- Mevcut PostgreSQL volume'larına audit log yapısını güvenle ekler.

CREATE SCHEMA IF NOT EXISTS audit_service;

CREATE TABLE IF NOT EXISTS audit_service.audit_logs (
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

CREATE INDEX IF NOT EXISTS idx_audit_logs_occurred_at
    ON audit_service.audit_logs (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor
    ON audit_service.audit_logs (actor_user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource
    ON audit_service.audit_logs (resource_type, resource_id, occurred_at DESC);
