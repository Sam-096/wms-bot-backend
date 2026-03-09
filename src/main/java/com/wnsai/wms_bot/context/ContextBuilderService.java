package com.wnsai.wms_bot.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a structured context block injected into LLM prompts.
 * Only called for AI_QUERY / UNKNOWN intent. Max 500 tokens of context.
 *
 * Keyword detection decides which DB tables to query —
 * we never load all data; only what's relevant to the question.
 *
 * TABLE NAMES match actual JPA entities (verified against @Table annotations):
 *   stock_inventory, inward_transactions, outward_transactions, gate_pass, bonds
 */
@Slf4j
@Service
public class ContextBuilderService implements ContextBuilder {

    @Autowired(required = false)
    private JdbcTemplate jdbc;

    @Override
    public Mono<String> buildContext(String message, String warehouseId, String role) {
        return Mono.fromCallable(() -> doBuild(message, warehouseId, role))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(ctx -> log.info("Context built: {} chars for warehouseId={}",
                ctx.length(), warehouseId));
    }

    private String doBuild(String message, String warehouseId, String role) {
        String lower = message.toLowerCase();
        List<String> sections = new ArrayList<>();

        // Only fetch sections relevant to the query keywords
        if (anyOf(lower, "stock", "inventory", "bags", "quantity", "available",
                         "item", "commodit", "ఎంత", "stock", "మాల")) {
            sections.add(fetchInventorySummary(warehouseId));
        }
        if (anyOf(lower, "inward", "receipt", "arrival", "pending", "received",
                         "truck", "incoming", "grn", "entry")) {
            sections.add(fetchRecentInward(warehouseId));
        }
        if (anyOf(lower, "outward", "dispatch", "delivery", "sent", "outgoing")) {
            sections.add(fetchRecentOutward(warehouseId));
        }
        if (anyOf(lower, "bond", "pledge", "lien", "collateral", "customs", "bonded")) {
            sections.add(fetchBondSummary(warehouseId));
        }
        if (anyOf(lower, "gate", "vehicle", "driver", "pass", "inside", "entry", "exit")) {
            sections.add(fetchGateStatus(warehouseId));
        }

        if (sections.isEmpty()) {
            return ""; // No specific context — let LLM answer from training
        }

        String context = String.join("\n\n", sections);
        // Hard cap at 500 tokens ≈ 2000 chars
        if (context.length() > 2000) {
            context = context.substring(0, 2000) + "\n... [context truncated]";
        }

        log.debug("Context block:\n{}", context);
        return "═══ LIVE WAREHOUSE CONTEXT ═══\n" + context + "\n═══════════════════════════════";
    }

    // ─── DB Fetchers — table names verified against JPA @Table annotations ────

    /**
     * Table: stock_inventory
     * Columns: item_name, current_stock, unit, min_threshold, warehouse_id
     */
    private String fetchInventorySummary(String warehouseId) {
        if (jdbc == null) return "INVENTORY: DB not connected";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT item_name, current_stock, unit, min_threshold
                FROM stock_inventory
                WHERE warehouse_id = ?
                  AND current_stock > 0
                ORDER BY current_stock DESC
                LIMIT 15
                """, warehouseId);
            if (rows.isEmpty()) return "INVENTORY: No stock items found for this warehouse.";
            StringBuilder sb = new StringBuilder("INVENTORY SUMMARY:\n");
            for (Map<String, Object> r : rows) {
                String low = "";
                Object threshold = r.get("min_threshold");
                Object stock = r.get("current_stock");
                if (threshold != null && stock != null) {
                    try {
                        double t = Double.parseDouble(threshold.toString());
                        double s = Double.parseDouble(stock.toString());
                        if (s < t) low = " ⚠️ LOW STOCK";
                    } catch (NumberFormatException ignored) {}
                }
                sb.append(String.format("  %s: %s %s%s\n",
                    r.get("item_name"), stock, r.get("unit"), low));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("fetchInventorySummary failed: {}", e.getMessage());
            return "INVENTORY: data temporarily unavailable";
        }
    }

    /**
     * Table: inward_transactions
     * Columns: grn_number, supplier_name, item_name, quantity, quantity_bags,
     *          unit, status, inward_date, vehicle_number, created_at
     */
    private String fetchRecentInward(String warehouseId) {
        if (jdbc == null) return "INWARD: DB not connected";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT grn_number, supplier_name, item_name,
                       quantity, quantity_bags, unit, status,
                       inward_date, vehicle_number
                FROM inward_transactions
                WHERE warehouse_id = ?
                ORDER BY created_at DESC
                LIMIT 5
                """, warehouseId);
            if (rows.isEmpty()) return "INWARD: No recent inward transactions.";
            StringBuilder sb = new StringBuilder("RECENT INWARD TRANSACTIONS:\n");
            for (Map<String, Object> r : rows) {
                sb.append(String.format("  GRN#%s | %s | %s | %s %s",
                    r.get("grn_number"), r.get("supplier_name"),
                    r.get("item_name"), r.get("quantity"), r.get("unit")));
                if (r.get("quantity_bags") != null)
                    sb.append(String.format(" (%s bags)", r.get("quantity_bags")));
                sb.append(String.format(" | %s | %s\n", r.get("status"), r.get("inward_date")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("fetchRecentInward failed: {}", e.getMessage());
            return "INWARD: data temporarily unavailable";
        }
    }

    /**
     * Table: outward_transactions
     * Columns: dispatch_number, customer_name, item_name, quantity, quantity_bags,
     *          unit, status, outward_date, vehicle_number, created_at
     */
    private String fetchRecentOutward(String warehouseId) {
        if (jdbc == null) return "OUTWARD: DB not connected";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT dispatch_number, customer_name, item_name,
                       quantity, quantity_bags, unit, status,
                       outward_date, vehicle_number
                FROM outward_transactions
                WHERE warehouse_id = ?
                ORDER BY created_at DESC
                LIMIT 5
                """, warehouseId);
            if (rows.isEmpty()) return "OUTWARD: No recent outward transactions.";
            StringBuilder sb = new StringBuilder("RECENT OUTWARD TRANSACTIONS:\n");
            for (Map<String, Object> r : rows) {
                sb.append(String.format("  Dispatch#%s | %s | %s | %s %s",
                    r.get("dispatch_number"), r.get("customer_name"),
                    r.get("item_name"), r.get("quantity"), r.get("unit")));
                if (r.get("quantity_bags") != null)
                    sb.append(String.format(" (%s bags)", r.get("quantity_bags")));
                sb.append(String.format(" | %s | %s\n", r.get("status"), r.get("outward_date")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("fetchRecentOutward failed: {}", e.getMessage());
            return "OUTWARD: data temporarily unavailable";
        }
    }

    /**
     * Table: bonds
     * Columns: bond_number, item_name, quantity, expiry_date, status
     */
    private String fetchBondSummary(String warehouseId) {
        if (jdbc == null) return "BONDS: DB not connected";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT bond_number, item_name, quantity, expiry_date, status
                FROM bonds
                WHERE warehouse_id = ? AND status = 'ACTIVE'
                ORDER BY expiry_date ASC
                LIMIT 5
                """, warehouseId);
            if (rows.isEmpty()) return "BONDS: No active bonds found.";
            StringBuilder sb = new StringBuilder("ACTIVE BONDS:\n");
            for (Map<String, Object> r : rows) {
                sb.append(String.format("  Bond#%s | %s | %s | Expires: %s\n",
                    r.get("bond_number"), r.get("item_name"),
                    r.get("quantity"), r.get("expiry_date")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("fetchBondSummary failed: {}", e.getMessage());
            return "BONDS: data temporarily unavailable";
        }
    }

    /**
     * Table: gate_pass
     * Columns: vehicle_number, driver_name, purpose, commodity_name,
     *          status (OPEN|CLOSED|CANCELLED), entry_time, exit_time
     */
    private String fetchGateStatus(String warehouseId) {
        if (jdbc == null) return "GATE: DB not connected";
        try {
            Integer open = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gate_pass
                WHERE warehouse_id = ? AND status = 'OPEN'
                """, Integer.class, warehouseId);

            List<Map<String, Object>> recent = jdbc.queryForList("""
                SELECT vehicle_number, driver_name, purpose,
                       commodity_name, status, entry_time
                FROM gate_pass
                WHERE warehouse_id = ?
                ORDER BY entry_time DESC
                LIMIT 5
                """, warehouseId);

            StringBuilder sb = new StringBuilder(
                String.format("GATE STATUS: %d vehicles currently inside (OPEN passes)\n", open));
            if (!recent.isEmpty()) {
                sb.append("RECENT GATE PASSES:\n");
                for (Map<String, Object> r : recent) {
                    sb.append(String.format("  %s | %s | %s | %s | %s\n",
                        r.get("vehicle_number"), r.get("driver_name"),
                        r.get("purpose"), r.get("commodity_name"), r.get("status")));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("fetchGateStatus failed: {}", e.getMessage());
            return "GATE: data temporarily unavailable";
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private boolean anyOf(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
