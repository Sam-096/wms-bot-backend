package com.wnsai.wms_bot.quick;

import com.wnsai.wms_bot.cache.GreetingResponseCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * Instant responses answered from DB — Ollama is NEVER called.
 *
 * DB queries run on boundedElastic scheduler (blocking JdbcTemplate).
 * If JdbcTemplate is not available (JPA disabled), falls back to
 * informative stub responses so the app still starts and runs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuickResponseService implements QuickResponder {

    private final GreetingResponseCache greetingCache;

    // Optional — null when datasource autoconfiguration is excluded
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private JdbcTemplate jdbc;

    // ─── Greeting ─────────────────────────────────────────────────────────────

    @Override
    public String greet(String language) {
        return greetingCache.get(language);
    }

    // ─── Quick Queries ────────────────────────────────────────────────────────

    @Override
    public Mono<String> quickStock(String warehouseId) {
        return Mono.fromCallable(() -> fetchLowStock(warehouseId))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(r -> log.info("quickStock warehouseId={} done", warehouseId));
    }

    @Override
    public Mono<String> quickPending(String warehouseId) {
        return Mono.fromCallable(() -> fetchPendingInward(warehouseId))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(r -> log.info("quickPending warehouseId={} done", warehouseId));
    }

    @Override
    public Mono<String> quickGatePasses(String warehouseId) {
        return Mono.fromCallable(() -> fetchActiveGatePasses(warehouseId))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(r -> log.info("quickGatePasses warehouseId={} done", warehouseId));
    }

    @Override
    public Mono<String> quickBonds(String warehouseId) {
        return Mono.fromCallable(() -> fetchExpiringBonds(warehouseId))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(r -> log.info("quickBonds warehouseId={} done", warehouseId));
    }

    @Override
    public Mono<String> handleQuickQuery(String entity, String warehouseId) {
        return switch (entity) {
            case "LOW_STOCK"         -> quickStock(warehouseId);
            case "PENDING_INWARD"    -> quickPending(warehouseId);
            case "TODAY_OUTWARD"     -> quickGatePasses(warehouseId);
            case "ACTIVE_GATE_PASSES"-> quickGatePasses(warehouseId);
            case "EXPIRING_BONDS"    -> quickBonds(warehouseId);
            default -> Mono.just("సమాచారం అందుబాటులో లేదు. Admin ని contact చేయండి 📞");
        };
    }

    // ─── DB Fetchers (blocking — must run on boundedElastic) ─────────────────

    private String fetchLowStock(String warehouseId) {
        if (jdbc == null) return stubLowStock();
        try {
            // TODO: adjust table/column names to match your actual schema
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT commodity_name, current_qty, unit
                FROM inventory_items
                WHERE warehouse_id = ? AND current_qty < min_qty
                ORDER BY current_qty ASC
                LIMIT 5
                """, warehouseId);

            if (rows.isEmpty()) return "✅ Stock levels are healthy. No low-stock items found.";

            StringBuilder sb = new StringBuilder("⚠️ Low Stock Alert (Top 5):\n");
            for (Map<String, Object> row : rows) {
                sb.append(String.format("• %s — %s %s\n",
                    row.get("commodity_name"),
                    row.get("current_qty"),
                    row.get("unit")));
            }
            sb.append("\n📋 Go to: Operations > Inventory");
            return sb.toString();

        } catch (Exception e) {
            log.warn("DB query failed for quickStock: {}", e.getMessage());
            return stubLowStock();
        }
    }

    private String fetchPendingInward(String warehouseId) {
        if (jdbc == null) return stubPendingInward();
        try {
            // TODO: adjust table/column names to match your actual schema
            Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM inward_receipts
                WHERE warehouse_id = ? AND status = 'PENDING'
                """, Integer.class, warehouseId);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT receipt_no, party_name, commodity, bags, created_at
                FROM inward_receipts
                WHERE warehouse_id = ? AND status = 'PENDING'
                ORDER BY created_at DESC
                LIMIT 5
                """, warehouseId);

            StringBuilder sb = new StringBuilder(
                String.format("📦 Pending Inward: %d total\n\n", count));
            for (Map<String, Object> row : rows) {
                sb.append(String.format("• #%s — %s | %s | %s bags\n",
                    row.get("receipt_no"),
                    row.get("party_name"),
                    row.get("commodity"),
                    row.get("bags")));
            }
            sb.append("\n📋 Go to: Operations > Inward Receipts");
            return sb.toString();

        } catch (Exception e) {
            log.warn("DB query failed for quickPending: {}", e.getMessage());
            return stubPendingInward();
        }
    }

    private String fetchActiveGatePasses(String warehouseId) {
        if (jdbc == null) return stubGatePasses();
        try {
            // TODO: adjust table/column names to match your actual schema
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT pass_no, vehicle_no, driver_name, purpose, entry_time
                FROM gate_passes
                WHERE warehouse_id = ? AND status = 'ACTIVE'
                ORDER BY entry_time DESC
                LIMIT 5
                """, warehouseId);

            if (rows.isEmpty()) return "✅ No active gate passes at this time.";

            StringBuilder sb = new StringBuilder(
                String.format("🚛 Active Gate Passes (%d shown):\n\n", rows.size()));
            for (Map<String, Object> row : rows) {
                sb.append(String.format("• #%s — %s | %s | %s\n",
                    row.get("pass_no"),
                    row.get("vehicle_no"),
                    row.get("driver_name"),
                    row.get("purpose")));
            }
            sb.append("\n📋 Go to: Gate Operations > Gate Pass Outs");
            return sb.toString();

        } catch (Exception e) {
            log.warn("DB query failed for quickGatePasses: {}", e.getMessage());
            return stubGatePasses();
        }
    }

    private String fetchExpiringBonds(String warehouseId) {
        if (jdbc == null) return stubBonds();
        try {
            // TODO: adjust table/column names to match your actual schema
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT bond_no, party_name, commodity, expiry_date,
                       EXTRACT(DAY FROM expiry_date - NOW()) AS days_left
                FROM bonds
                WHERE warehouse_id = ? AND status = 'ACTIVE'
                  AND expiry_date BETWEEN NOW() AND NOW() + INTERVAL '7 days'
                ORDER BY expiry_date ASC
                """, warehouseId);

            if (rows.isEmpty()) return "✅ No bonds expiring in the next 7 days.";

            StringBuilder sb = new StringBuilder("⚠️ Bonds Expiring in 7 Days:\n\n");
            for (Map<String, Object> row : rows) {
                sb.append(String.format("• #%s — %s | %s | Expires: %s (%s days)\n",
                    row.get("bond_no"),
                    row.get("party_name"),
                    row.get("commodity"),
                    row.get("expiry_date"),
                    row.get("days_left") != null
                        ? ((Number) row.get("days_left")).intValue() : "?"));
            }
            sb.append("\n📋 Go to: Bonds > [Select Bond] > Extend");
            return sb.toString();

        } catch (Exception e) {
            log.warn("DB query failed for quickBonds: {}", e.getMessage());
            return stubBonds();
        }
    }

    // ─── Stub responses when DB is unavailable ────────────────────────────────

    private String stubLowStock() {
        return """
            ⚠️ Low Stock Alert (sample data — connect DB for live data):
            • Wheat — 50 bags
            • Rice — 20 bags
            • Maize — 15 bags

            📋 Go to: Operations > Inventory for live stock levels.
            """.strip();
    }

    private String stubPendingInward() {
        return """
            📦 Pending Inward: 3 receipts (sample — connect DB for live data)
            • #IR-001 — ABC Traders | Wheat | 500 bags
            • #IR-002 — XYZ Farm | Rice | 200 bags

            📋 Go to: Operations > Inward Receipts to view all.
            """.strip();
    }

    private String stubGatePasses() {
        return """
            🚛 Active Gate Passes: 2 (sample — connect DB for live data)
            • #GP-101 — TN-01-AB-1234 | Raju | Inward
            • #GP-102 — AP-09-CD-5678 | Suresh | Outward

            📋 Go to: Gate Operations > Gate Pass Outs.
            """.strip();
    }

    private String stubBonds() {
        return """
            ⚠️ Bonds Expiring Soon (sample — connect DB for live data):
            • #BOND-201 — HDFC Bank | Wheat | Expires: 7 days

            📋 Go to: Bonds to view and extend.
            """.strip();
    }
}
