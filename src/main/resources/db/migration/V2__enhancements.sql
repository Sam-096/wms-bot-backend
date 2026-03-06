-- =============================================================================
-- V2__enhancements.sql
-- Apply manually on Supabase SQL Editor, or via Flyway if configured.
-- =============================================================================

-- ── Users table ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  UNIQUE NOT NULL,
    email         VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('MANAGER','OPERATOR','GATE_STAFF','VIEWER')),
    warehouse_id  VARCHAR(50),
    is_active     BOOLEAN      DEFAULT TRUE,
    created_at    TIMESTAMP    DEFAULT NOW(),
    last_login    TIMESTAMP,
    refresh_token VARCHAR(500)
);

-- ── Chat session enhancements ─────────────────────────────────────────────────
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS title       VARCHAR(100);
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS is_deleted  BOOLEAN DEFAULT FALSE;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS user_id     UUID REFERENCES users(id);

-- ── Chat message enhancements ─────────────────────────────────────────────────
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS response_type      VARCHAR(20) DEFAULT 'TEXT';
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS ai_provider        VARCHAR(20);
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS processing_time_ms INTEGER;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS was_helpful        BOOLEAN;

-- ── Inward transaction enhancements ──────────────────────────────────────────
ALTER TABLE inward_transactions ADD COLUMN IF NOT EXISTS vehicle_number  VARCHAR(30);
ALTER TABLE inward_transactions ADD COLUMN IF NOT EXISTS quantity_bags   INTEGER;
ALTER TABLE inward_transactions ADD COLUMN IF NOT EXISTS unit_weight     NUMERIC(10,3);
ALTER TABLE inward_transactions ADD COLUMN IF NOT EXISTS total_weight    NUMERIC(12,3);
ALTER TABLE inward_transactions ADD COLUMN IF NOT EXISTS remarks         TEXT;
ALTER TABLE inward_transactions ADD COLUMN IF NOT EXISTS approved_by     UUID REFERENCES users(id);
ALTER TABLE inward_transactions ADD COLUMN IF NOT EXISTS approved_at     TIMESTAMP;

-- ── Outward transaction enhancements ─────────────────────────────────────────
ALTER TABLE outward_transactions ADD COLUMN IF NOT EXISTS vehicle_number  VARCHAR(30);
ALTER TABLE outward_transactions ADD COLUMN IF NOT EXISTS quantity_bags   INTEGER;
ALTER TABLE outward_transactions ADD COLUMN IF NOT EXISTS unit_weight     NUMERIC(10,3);
ALTER TABLE outward_transactions ADD COLUMN IF NOT EXISTS total_weight    NUMERIC(12,3);
ALTER TABLE outward_transactions ADD COLUMN IF NOT EXISTS remarks         TEXT;
ALTER TABLE outward_transactions ADD COLUMN IF NOT EXISTS approved_by     UUID REFERENCES users(id);
ALTER TABLE outward_transactions ADD COLUMN IF NOT EXISTS approved_at     TIMESTAMP;

-- ── Gate pass enhancements ────────────────────────────────────────────────────
ALTER TABLE gate_pass ADD COLUMN IF NOT EXISTS purpose                 VARCHAR(50);
ALTER TABLE gate_pass ADD COLUMN IF NOT EXISTS commodity_name          VARCHAR(200);
ALTER TABLE gate_pass ADD COLUMN IF NOT EXISTS bags_count              INTEGER;
ALTER TABLE gate_pass ADD COLUMN IF NOT EXISTS operator_id             UUID REFERENCES users(id);
ALTER TABLE gate_pass ADD COLUMN IF NOT EXISTS inward_transaction_id   UUID;
ALTER TABLE gate_pass ADD COLUMN IF NOT EXISTS outward_transaction_id  UUID;

-- ── Generated reports table ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS generated_reports (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id VARCHAR(50),
    user_id      UUID         REFERENCES users(id),
    report_type  VARCHAR(50),
    format       VARCHAR(10),
    status       VARCHAR(20)  DEFAULT 'GENERATING',
    file_path    VARCHAR(500),
    error_message TEXT,
    generated_at TIMESTAMP    DEFAULT NOW(),
    expires_at   TIMESTAMP    DEFAULT (NOW() + INTERVAL '1 hour')
);

-- ── Performance indexes ───────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_inward_warehouse_status
    ON inward_transactions(warehouse_id, status);
CREATE INDEX IF NOT EXISTS idx_outward_warehouse_status
    ON outward_transactions(warehouse_id, status);
CREATE INDEX IF NOT EXISTS idx_gate_pass_status
    ON gate_pass(warehouse_id, status);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_warehouse
    ON chat_sessions(warehouse_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_chat_messages_session
    ON chat_messages(session_id);
CREATE INDEX IF NOT EXISTS idx_users_email
    ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_warehouse
    ON users(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_generated_reports_user
    ON generated_reports(user_id, generated_at DESC);
