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
 * All SQL uses the actual schema derived from JPA entity column mappings:
 *   stock_inventory      — item_name, current_stock, min_threshold, unit
 *   inward_transactions  — grn_number, supplier_name, item_name, quantity_bags, status, inward_date
 *   outward_transactions — dispatch_number, customer_name, item_name, quantity_bags, outward_date, status, approved_at
 *   gate_pass            — pass_number, vehicle_number, driver_name, purpose, status, entry_time
 *   bonds                — bond_number, item_name, expiry_date, status
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
    public Mono<String> quickOutward(String warehouseId) {
        return Mono.fromCallable(() -> fetchTodayOutward(warehouseId))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(r -> log.info("quickOutward warehouseId={} done", warehouseId));
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
        if (entity == null) return Mono.just("సమాచారం అందుబాటులో లేదు. Admin ని contact చేయండి 📞");
        return switch (entity) {
            case "LOW_STOCK"          -> quickStock(warehouseId);
            case "PENDING_INWARD"     -> quickPending(warehouseId);
            case "TODAY_OUTWARD"      -> quickOutward(warehouseId);
            case "ACTIVE_GATE_PASSES" -> quickGatePasses(warehouseId);
            case "EXPIRING_BONDS"     -> quickBonds(warehouseId);
            default -> Mono.just("సమాచారం అందుబాటులో లేదు. Admin ని contact చేయండి 📞");
        };
    }

    // ─── DB Fetchers (blocking — must run on boundedElastic) ─────────────────

    private String fetchLowStock(String warehouseId) {
        if (jdbc == null) return dbUnavailable("Inventory");
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT item_name, current_stock, unit
                FROM stock_inventory
                WHERE warehouse_id = ? AND current_stock < min_threshold
                ORDER BY current_stock ASC
                LIMIT 5
                """, warehouseId);

            if (rows.isEmpty()) return "✅ Stock levels are healthy. No low-stock items found.";

            StringBuilder sb = new StringBuilder("⚠️ Low Stock Alert (Top 5):\n");
            for (Map<String, Object> row : rows) {
                sb.append(String.format("• %s — %s %s\n",
                    row.get("item_name"),
                    row.get("current_stock"),
                    row.get("unit")));
            }
            sb.append("\n📋 Go to: Operations > Inventory");
            return sb.toString();

        } catch (Exception e) {
            log.warn("DB query failed for quickStock wh={}: {}", warehouseId, e.getMessage());
            return dbError("Inventory");
        }
    }

    private String fetchPendingInward(String warehouseId) {
        if (jdbc == null) return dbUnavailable("Inward Receipts");
        try {
            Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM inward_transactions
                WHERE warehouse_id = ? AND status = 'PENDING'
                """, Integer.class, warehouseId);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT grn_number, supplier_name, item_name, quantity_bags, inward_date
                FROM inward_transactions
                WHERE warehouse_id = ? AND status = 'PENDING'
                ORDER BY inward_date DESC
                LIMIT 5
                """, warehouseId);

            int total = count != null ? count : 0;
            if (total == 0) return "✅ No pending inward receipts.";

            StringBuilder sb = new StringBuilder(
                String.format("📦 Pending Inward: %d total\n\n", total));
            for (Map<String, Object> row : rows) {
                sb.append(String.format("• %s — %s | %s | %s bags\n",
                    row.get("grn_number"),
                    row.get("supplier_name"),
                    row.get("item_name"),
                    row.get("quantity_bags")));
            }
            sb.append("\n📋 Go to: Operations > Inward Receipts");
            return sb.toString();

        } catch (Exception e) {
            log.warn("DB query failed for quickPending wh={}: {}", warehouseId, e.getMessage());
            return dbError("Inward Receipts");
        }
    }

    private String fetchTodayOutward(String warehouseId) {
        if (jdbc == null) return dbUnavailable("Outward Dispatch");
        try {
            Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outward_transactions
                WHERE warehouse_id = ? AND status = 'APPROVED'
                  AND DATE(approved_at) = CURRENT_DATE
                """, Integer.class, warehouseId);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT dispatch_number, customer_name, item_name, quantity_bags
                FROM outward_transactions
                WHERE warehouse_id = ? AND status = 'APPROVED'
                  AND DATE(approved_at) = CURRENT_DATE
                ORDER BY approved_at DESC
                LIMIT 5
                """, warehouseId);

            int total = count != null ? count : 0;
            if (total == 0) return "✅ No dispatches approved today.";

            StringBuilder sb = new StringBuilder(
                String.format("🚚 Today's Dispatches: %d approved\n\n", total));
            for (Map<String, Object> row : rows) {
                sb.append(String.format("• %s — %s | %s | %s bags\n",
                    row.get("dispatch_number"),
                    row.get("customer_name"),
                    row.get("item_name"),
                    row.get("quantity_bags")));
            }
            sb.append("\n📋 Go to: Operations > Outward Dispatch");
            return sb.toString();

        } catch (Exception e) {
            log.warn("DB query failed for quickOutward wh={}: {}", warehouseId, e.getMessage());
            return dbError("Outward Dispatch");
        }
    }

    private String fetchActiveGatePasses(String warehouseId) {
        if (jdbc == null) return dbUnavailable("Gate Operations");
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT pass_number, vehicle_number, driver_name, purpose, entry_time
                FROM gate_pass
                WHERE warehouse_id = ? AND status = 'OPEN'
                ORDER BY entry_time DESC
                LIMIT 5
                """, warehouseId);

            if (rows.isEmpty()) return "✅ No active gate passes at this time.";

            StringBuilder sb = new StringBuilder(
                String.format("🚛 Active Gate Passes (%d shown):\n\n", rows.size()));
            for (Map<String, Object> row : rows) {
                sb.append(String.format("• %s — %s | %s | %s\n",
                    row.get("pass_number"),
                    row.get("vehicle_number"),
                    row.get("driver_name"),
                    row.get("purpose")));
            }
            sb.append("\n📋 Go to: Gate Operations > Gate Pass Outs");
            return sb.toString();

        } catch (Exception e) {
            log.warn("DB query failed for quickGatePasses wh={}: {}", warehouseId, e.getMessage());
            return dbError("Gate Operations");
        }
    }

    private String fetchExpiringBonds(String warehouseId) {
        if (jdbc == null) return dbUnavailable("Bonds");
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT bond_number, item_name, expiry_date,
                       (expiry_date - CURRENT_DATE) AS days_left
                FROM bonds
                WHERE warehouse_id = ? AND status = 'ACTIVE'
                  AND expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days'
                ORDER BY expiry_date ASC
                """, warehouseId);

            if (rows.isEmpty()) return "✅ No bonds expiring in the next 7 days.";

            StringBuilder sb = new StringBuilder("⚠️ Bonds Expiring in 7 Days:\n\n");
            for (Map<String, Object> row : rows) {
                sb.append(String.format("• %s — %s | Expires: %s (%s days)\n",
                    row.get("bond_number"),
                    row.get("item_name"),
                    row.get("expiry_date"),
                    row.get("days_left") != null
                        ? ((Number) row.get("days_left")).intValue() : "?"));
            }
            sb.append("\n📋 Go to: Bonds > [Select Bond] > Extend");
            return sb.toString();

        } catch (Exception e) {
            log.warn("DB query failed for quickBonds wh={}: {}", warehouseId, e.getMessage());
            return dbError("Bonds");
        }
    }

    // ─── Error messages ───────────────────────────────────────────────────────

    private String dbUnavailable(String module) {
        return String.format("⚠️ Live data unavailable. Go to: %s for current information.", module);
    }

    private String dbError(String module) {
        return String.format("⚠️ Could not fetch live data. Please check %s directly.", module);
    }
}
