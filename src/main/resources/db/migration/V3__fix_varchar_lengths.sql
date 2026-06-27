-- =============================================================================
-- V3__fix_varchar_lengths.sql
-- Apply on Supabase SQL Editor (or Flyway if configured).
--
-- Root cause: chat_messages.warehouse_id and chat_sessions.warehouse_id were
-- originally VARCHAR(20), suitable for short codes like "WH-001". The warehouse
-- context resolver now returns UUID-format warehouse IDs (36 chars) from the
-- users table, causing "value too long for type character varying(20)" on every
-- chat_messages INSERT.
--
-- Also widens session_id defensively: session IDs are UUID strings (36 chars)
-- and may have been created with a narrow type in V1.
--
-- These are safe widening operations — no data is lost, no USING clause needed.
-- =============================================================================

-- ── chat_messages ─────────────────────────────────────────────────────────────
ALTER TABLE chat_messages
    ALTER COLUMN warehouse_id TYPE TEXT,
    ALTER COLUMN session_id   TYPE TEXT,
    ALTER COLUMN intent       TYPE VARCHAR(50),
    ALTER COLUMN language     TYPE VARCHAR(10);

-- ── chat_sessions ─────────────────────────────────────────────────────────────
-- Defensive: session.warehouse_id receives the same UUID values during
-- WarehouseContextResolver upgrade of UNKNOWN sessions.
ALTER TABLE chat_sessions
    ALTER COLUMN warehouse_id TYPE TEXT,
    ALTER COLUMN session_id   TYPE TEXT;
