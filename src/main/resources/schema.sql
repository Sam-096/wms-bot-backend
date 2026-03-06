-- ═══════════════════════════════════════════════════════════════════════════
-- Godown AI — PostgreSQL schema with pgvector
-- Run once against your PostgreSQL instance:
--   psql -U postgres -d wmsbot -f schema.sql
-- ═══════════════════════════════════════════════════════════════════════════

-- pgvector extension (requires pgvector installed on the server)
CREATE EXTENSION IF NOT EXISTS vector;

-- ─── RAG Embeddings Table ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS wms_embeddings (
    id          BIGSERIAL PRIMARY KEY,
    source      VARCHAR(200)  NOT NULL,       -- e.g. "faq", "sop", "procedure"
    content     TEXT          NOT NULL,       -- the document chunk
    embedding   VECTOR(2048),                 -- llama3.2:3b embedding dimension
    created_at  TIMESTAMPTZ   DEFAULT NOW()
);

-- IVFFlat index for approximate nearest-neighbour search
CREATE INDEX IF NOT EXISTS wms_embeddings_embedding_idx
    ON wms_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- ─── Warehouses ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS warehouses (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(200)  NOT NULL,
    location      VARCHAR(500),
    license_no    VARCHAR(100),
    capacity_mt   NUMERIC(12,2),
    created_at    TIMESTAMPTZ   DEFAULT NOW()
);

-- ─── Inventory Items ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS inventory_items (
    id              BIGSERIAL PRIMARY KEY,
    warehouse_id    UUID          NOT NULL REFERENCES warehouses(id),
    commodity_name  VARCHAR(200)  NOT NULL,
    current_qty     NUMERIC(12,2) NOT NULL DEFAULT 0,
    min_qty         NUMERIC(12,2) NOT NULL DEFAULT 0,   -- low-stock threshold
    unit            VARCHAR(20)   NOT NULL DEFAULT 'MT',
    lot_no          VARCHAR(100),
    stack_no        VARCHAR(50),
    updated_at      TIMESTAMPTZ   DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_inventory_warehouse
    ON inventory_items(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_inventory_low_stock
    ON inventory_items(warehouse_id, current_qty)
    WHERE current_qty < min_qty;

-- ─── Inward Receipts ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS inward_receipts (
    id            BIGSERIAL     PRIMARY KEY,
    warehouse_id  UUID          NOT NULL REFERENCES warehouses(id),
    receipt_no    VARCHAR(50)   NOT NULL UNIQUE,
    party_name    VARCHAR(200),
    commodity     VARCHAR(200),
    bags          INTEGER,
    weight_mt     NUMERIC(12,2),
    vehicle_no    VARCHAR(30),
    status        VARCHAR(20)   DEFAULT 'PENDING',  -- PENDING | COMPLETE | REJECTED
    created_at    TIMESTAMPTZ   DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_inward_warehouse_status
    ON inward_receipts(warehouse_id, status);

-- ─── Outward Dispatches ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS outward_dispatches (
    id            BIGSERIAL     PRIMARY KEY,
    warehouse_id  UUID          NOT NULL REFERENCES warehouses(id),
    dispatch_no   VARCHAR(50)   NOT NULL UNIQUE,
    party_name    VARCHAR(200),
    commodity     VARCHAR(200),
    bags          INTEGER,
    weight_mt     NUMERIC(12,2),
    vehicle_no    VARCHAR(30),
    status        VARCHAR(20)   DEFAULT 'PENDING',  -- PENDING | DISPATCHED | CANCELLED
    created_at    TIMESTAMPTZ   DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outward_warehouse_status
    ON outward_dispatches(warehouse_id, status);

-- ─── Gate Passes ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS gate_passes (
    id            BIGSERIAL     PRIMARY KEY,
    warehouse_id  UUID          NOT NULL REFERENCES warehouses(id),
    pass_no       VARCHAR(50)   NOT NULL UNIQUE,
    vehicle_no    VARCHAR(30),
    driver_name   VARCHAR(200),
    purpose       VARCHAR(50),                      -- INWARD | OUTWARD | TRANSFER
    status        VARCHAR(20)   DEFAULT 'ACTIVE',   -- ACTIVE | EXITED | CANCELLED
    entry_time    TIMESTAMPTZ   DEFAULT NOW(),
    exit_time     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_gate_passes_warehouse_status
    ON gate_passes(warehouse_id, status);

-- ─── Bonds ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bonds (
    id                BIGSERIAL     PRIMARY KEY,
    warehouse_id      UUID          NOT NULL REFERENCES warehouses(id),
    bond_no           VARCHAR(100)  NOT NULL UNIQUE,
    party_name        VARCHAR(200),
    commodity         VARCHAR(200),
    collateral_value  NUMERIC(15,2),
    status            VARCHAR(20)   DEFAULT 'ACTIVE',  -- ACTIVE | EXPIRED | CLOSED
    created_at        TIMESTAMPTZ   DEFAULT NOW(),
    expiry_date       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bonds_warehouse_expiry
    ON bonds(warehouse_id, expiry_date)
    WHERE status = 'ACTIVE';

-- ─── Sample seed data (optional — remove for production) ─────────────────────
-- INSERT INTO warehouses (id, name, location) VALUES
--   ('00000000-0000-0000-0000-000000000001', 'Demo Warehouse', 'Hyderabad');
