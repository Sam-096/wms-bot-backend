-- ==========================================================================
--  WMS Test-Data Seed — runs in Supabase SQL Editor (and psql)
-- ==========================================================================
--  HOW TO USE:
--    1. Find an existing warehouse_id:
--           SELECT warehouse_id FROM warehouses LIMIT 10;
--    2. Change 'WH-HYD-001' on the target_wh line below to match.
--    3. Paste the whole file into Supabase SQL Editor → Run.
--    4. Scroll to the bottom for row-count confirmation.
--
--  All rows carry a SEED- prefix on human-readable IDs (grn_number,
--  dispatch_number, pass_number, bond_number, item_code) so they can be
--  bulk-deleted later via the cleanup block at the end.
--
--  Volume added per run (~375 rows):
--     stock_inventory         45
--     inward_transactions    120
--     outward_transactions   120
--     gate_pass               70
--     bonds                   20
--
--  Safety:
--    - Wrapped in a single DO block + BEGIN/COMMIT → any error rolls back
--    - Idempotent via WHERE NOT EXISTS → safe to re-run
--    - Does NOT truncate or modify any existing non-SEED rows
-- ==========================================================================

BEGIN;

DO $seed$
DECLARE
    -- >>>>> EDIT THIS to a warehouse_id that exists in your warehouses table <<<<<
    target_wh TEXT := 'WH-001';
BEGIN

    IF NOT EXISTS (SELECT 1 FROM warehouses WHERE warehouse_id = target_wh) THEN
        RAISE EXCEPTION 'warehouse_id % not found. Update target_wh at the top of this script.', target_wh;
    END IF;

    -- ------------------------------------------------------------------
    -- 1. STOCK INVENTORY — 45 SKUs, several below min_threshold (low-stock)
    -- ------------------------------------------------------------------
    INSERT INTO stock_inventory (id, warehouse_id, item_name, item_code, current_stock, min_threshold, unit, last_updated)
    SELECT gen_random_uuid(), target_wh, item, code, stock, threshold, unit, NOW()
    FROM (VALUES
        ('Wheat',              'SEED-SKU-001', 12400.000,  2000.000, 'BAGS'),
        ('Paddy (Raw)',        'SEED-SKU-002',  8900.000,  1500.000, 'BAGS'),
        ('Basmati Rice',       'SEED-SKU-003',  3400.000,  1000.000, 'BAGS'),
        ('Sona Masuri Rice',   'SEED-SKU-004',  6750.000,  1200.000, 'BAGS'),
        ('Moong Dal',          'SEED-SKU-005',   180.000,   500.000, 'BAGS'),
        ('Toor Dal',           'SEED-SKU-006',   420.000,   600.000, 'BAGS'),
        ('Urad Dal',           'SEED-SKU-007',  1200.000,   400.000, 'BAGS'),
        ('Chana Dal',          'SEED-SKU-008',   950.000,   500.000, 'BAGS'),
        ('Masoor Dal',         'SEED-SKU-009',   340.000,   400.000, 'BAGS'),
        ('Groundnut',          'SEED-SKU-010',  5400.000,   800.000, 'BAGS'),
        ('Mustard Seed',       'SEED-SKU-011',  1800.000,   500.000, 'BAGS'),
        ('Sesame',             'SEED-SKU-012',   620.000,   300.000, 'BAGS'),
        ('Sunflower Seed',     'SEED-SKU-013',  2100.000,   600.000, 'BAGS'),
        ('Castor Seed',        'SEED-SKU-014',   890.000,   400.000, 'BAGS'),
        ('Cotton Seed',        'SEED-SKU-015',  3200.000,   700.000, 'BAGS'),
        ('Maize',              'SEED-SKU-016',  4500.000,   800.000, 'BAGS'),
        ('Jowar',              'SEED-SKU-017',  1600.000,   500.000, 'BAGS'),
        ('Bajra',              'SEED-SKU-018',  1100.000,   400.000, 'BAGS'),
        ('Ragi',               'SEED-SKU-019',   720.000,   300.000, 'BAGS'),
        ('Red Chilli',         'SEED-SKU-020',   240.000,   200.000, 'BAGS'),
        ('Turmeric',           'SEED-SKU-021',   580.000,   250.000, 'BAGS'),
        ('Coriander',          'SEED-SKU-022',   410.000,   200.000, 'BAGS'),
        ('Cumin',              'SEED-SKU-023',    90.000,   150.000, 'BAGS'),
        ('Fenugreek',          'SEED-SKU-024',   130.000,   100.000, 'BAGS'),
        ('Cardamom',           'SEED-SKU-025',    45.000,    80.000, 'BAGS'),
        ('Black Pepper',       'SEED-SKU-026',   310.000,   150.000, 'BAGS'),
        ('Clove',              'SEED-SKU-027',    70.000,    50.000, 'BAGS'),
        ('Cinnamon',           'SEED-SKU-028',   110.000,    80.000, 'BAGS'),
        ('Tamarind',           'SEED-SKU-029',   820.000,   300.000, 'BAGS'),
        ('Jaggery',            'SEED-SKU-030',  1400.000,   400.000, 'BAGS'),
        ('Sugar',              'SEED-SKU-031',  7800.000,  1000.000, 'BAGS'),
        ('Salt (Industrial)',  'SEED-SKU-032',  9200.000,  1500.000, 'BAGS'),
        ('Tea Leaves',         'SEED-SKU-033',   340.000,   200.000, 'BAGS'),
        ('Coffee Beans',       'SEED-SKU-034',   410.000,   250.000, 'BAGS'),
        ('Cashew',             'SEED-SKU-035',   160.000,   200.000, 'BAGS'),
        ('Almond',             'SEED-SKU-036',   220.000,   180.000, 'BAGS'),
        ('Raisin',             'SEED-SKU-037',   380.000,   150.000, 'BAGS'),
        ('Dates',              'SEED-SKU-038',   290.000,   200.000, 'BAGS'),
        ('Rice Bran',          'SEED-SKU-039',  2400.000,   600.000, 'BAGS'),
        ('Wheat Bran',         'SEED-SKU-040',  1900.000,   500.000, 'BAGS'),
        ('Soya Bean',          'SEED-SKU-041',  3600.000,   800.000, 'BAGS'),
        ('Pea (Yellow)',       'SEED-SKU-042',  1200.000,   400.000, 'BAGS'),
        ('Rajma',              'SEED-SKU-043',   510.000,   300.000, 'BAGS'),
        ('Kabuli Chana',       'SEED-SKU-044',   780.000,   400.000, 'BAGS'),
        ('Broken Rice',        'SEED-SKU-045',  2800.000,   700.000, 'BAGS')
    ) AS t(item, code, stock, threshold, unit)
    WHERE NOT EXISTS (
        SELECT 1 FROM stock_inventory
        WHERE warehouse_id = target_wh AND item_code = t.code
    );

    -- ------------------------------------------------------------------
    -- 2. INWARD TRANSACTIONS — 120 rows over last 30 days
    -- ------------------------------------------------------------------
    INSERT INTO inward_transactions (
        id, warehouse_id, grn_number, supplier_name, item_name, quantity, unit,
        status, inward_date
    )
    SELECT
        gen_random_uuid(),
        target_wh,
        'SEED-GRN-' || lpad(gs::text, 6, '0'),
        (ARRAY['Reliance Agro Pvt Ltd','ITC Ltd','Adani Wilmar','Cargill India','Godrej Agrovet',
               'Karnataka Agro','AP Farmers Coop','Bharti Enterprises','Tata Consumer','Patanjali Foods'])
            [1 + (gs % 10)],
        (ARRAY['Wheat','Paddy (Raw)','Basmati Rice','Sona Masuri Rice','Moong Dal','Toor Dal',
               'Groundnut','Maize','Sugar','Turmeric'])[1 + (gs % 10)],
        (500 + (gs * 37) % 4500)::numeric,
        'BAGS',
        CASE (gs % 10)
            WHEN 0 THEN 'PENDING'
            WHEN 1 THEN 'PENDING'
            WHEN 2 THEN 'REJECTED'
            ELSE 'APPROVED'
        END,
        (CURRENT_DATE - ((gs % 30) || ' days')::interval)::date
    FROM generate_series(1, 120) AS gs
    WHERE NOT EXISTS (
        SELECT 1 FROM inward_transactions WHERE grn_number = 'SEED-GRN-' || lpad(gs::text, 6, '0')
    );

    -- ------------------------------------------------------------------
    -- 3. OUTWARD TRANSACTIONS — 120 rows over last 30 days
    -- ------------------------------------------------------------------
    INSERT INTO outward_transactions (
        id, warehouse_id, dispatch_number, customer_name, item_name, quantity, unit,
        status, outward_date
    )
    SELECT
        gen_random_uuid(),
        target_wh,
        'SEED-DSP-' || lpad(gs::text, 6, '0'),
        (ARRAY['Big Basket','Reliance Retail','DMart','More Supermarket','Spencers',
               'Heritage Foods','Metro Cash & Carry','Local Distributor-HYD','Kurnool Rice Mill','Vijayawada Trader'])
            [1 + (gs % 10)],
        (ARRAY['Basmati Rice','Sona Masuri Rice','Wheat','Moong Dal','Toor Dal',
               'Sugar','Groundnut','Maize','Turmeric','Paddy (Raw)'])[1 + (gs % 10)],
        (300 + (gs * 29) % 3800)::numeric,
        'BAGS',
        CASE (gs % 8)
            WHEN 0 THEN 'PENDING'
            WHEN 1 THEN 'PENDING'
            ELSE 'APPROVED'
        END,
        (CURRENT_DATE - ((gs % 30) || ' days')::interval)::date
    FROM generate_series(1, 120) AS gs
    WHERE NOT EXISTS (
        SELECT 1 FROM outward_transactions WHERE dispatch_number = 'SEED-DSP-' || lpad(gs::text, 6, '0')
    );

    -- ------------------------------------------------------------------
    -- 4. GATE PASS — 70 rows (20 OPEN, 40 CLOSED, 10 CANCELLED)
    -- ------------------------------------------------------------------
    INSERT INTO gate_pass (
        id, warehouse_id, pass_number, vehicle_number, driver_name, pass_type,
        status, entry_time, exit_time
    )
    SELECT
        gen_random_uuid(),
        target_wh,
        'SEED-GP-' || lpad(gs::text, 6, '0'),
        (ARRAY['AP28AB1234','AP28CD5678','TS09EF9012','TS09GH3456','KA01IJ7890',
               'MH12KL2345','GJ05MN6789','TN11OP0123','KL07QR4567','AP39ST8901'])[1 + (gs % 10)],
        (ARRAY['Ramesh Kumar','Suresh Reddy','Mahesh Singh','Vijay Rao','Prakash Naidu',
               'Anil Patel','Rajesh Sharma','Sunil Yadav','Kiran Babu','Naveen Kumar'])[1 + (gs % 10)],
        (ARRAY['IN','OUT','TRANSFER'])[1 + (gs % 3)],
        CASE
            WHEN gs <= 20 THEN 'OPEN'
            WHEN gs <= 60 THEN 'CLOSED'
            ELSE 'CANCELLED'
        END,
        (NOW() - ((gs % 20) || ' hours')::interval - ((gs % 15) || ' days')::interval),
        CASE
            WHEN gs <= 20 THEN NULL
            ELSE (NOW() - ((gs % 10) || ' hours')::interval - ((gs % 14) || ' days')::interval)
        END
    FROM generate_series(1, 70) AS gs
    WHERE NOT EXISTS (
        SELECT 1 FROM gate_pass WHERE pass_number = 'SEED-GP-' || lpad(gs::text, 6, '0')
    );

    -- ------------------------------------------------------------------
    -- 5. BONDS — 20 rows, 5 expiring in next 30 days
    -- ------------------------------------------------------------------
    INSERT INTO bonds (
        id, warehouse_id, bond_number, item_name, quantity, bond_date, expiry_date, status
    )
    SELECT
        gen_random_uuid(),
        target_wh,
        'SEED-BOND-' || lpad(gs::text, 6, '0'),
        (ARRAY['Wheat','Paddy (Raw)','Basmati Rice','Sona Masuri Rice','Sugar',
               'Groundnut','Maize','Turmeric','Soya Bean','Cotton Seed'])[1 + (gs % 10)],
        (5000 + (gs * 113) % 45000)::numeric,
        (CURRENT_DATE - ((gs % 180) + 30 || ' days')::interval)::date,
        CASE
            WHEN gs <= 5  THEN (CURRENT_DATE + ((gs * 5) || ' days')::interval)::date
            WHEN gs <= 15 THEN (CURRENT_DATE + ((gs * 20) || ' days')::interval)::date
            ELSE (CURRENT_DATE - ((gs % 30) || ' days')::interval)::date
        END,
        CASE
            WHEN gs <= 15 THEN 'ACTIVE'
            WHEN gs <= 18 THEN 'EXPIRED'
            ELSE 'CLOSED'
        END
    FROM generate_series(1, 20) AS gs
    WHERE NOT EXISTS (
        SELECT 1 FROM bonds WHERE bond_number = 'SEED-BOND-' || lpad(gs::text, 6, '0')
    );

END $seed$;

COMMIT;

-- ==========================================================================
--  Verification — run this separately after the commit to see row counts
-- ==========================================================================
SELECT 'stock_inventory'      AS tbl, COUNT(*) AS total_rows FROM stock_inventory
UNION ALL SELECT 'inward_transactions',  COUNT(*) FROM inward_transactions
UNION ALL SELECT 'outward_transactions', COUNT(*) FROM outward_transactions
UNION ALL SELECT 'gate_pass',            COUNT(*) FROM gate_pass
UNION ALL SELECT 'bonds',                COUNT(*) FROM bonds;

-- ==========================================================================
--  CLEANUP — uncomment and run to remove every SEED- row (any warehouse)
-- ==========================================================================
-- BEGIN;
-- DELETE FROM stock_inventory      WHERE item_code       LIKE 'SEED-SKU-%';
-- DELETE FROM inward_transactions  WHERE grn_number      LIKE 'SEED-GRN-%';
-- DELETE FROM outward_transactions WHERE dispatch_number LIKE 'SEED-DSP-%';
-- DELETE FROM gate_pass            WHERE pass_number     LIKE 'SEED-GP-%';
-- DELETE FROM bonds                WHERE bond_number     LIKE 'SEED-BOND-%';
-- COMMIT;
