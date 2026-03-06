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
 * Builds a structured context block injected into Ollama prompts.
 * Only called for AI_QUERY intent. Max 500 tokens of context.
 *
 * Keyword detection decides which DB tables to query —
 * we never load all data; only what's relevant to the question.
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
        if (anyOf(lower, "stock", "inventory", "bags", "quantity", "ఎంత")) {
            sections.add(fetchInventorySummary(warehouseId));
        }
        if (anyOf(lower, "inward", "receipt", "arrival", "pending")) {
            sections.add(fetchRecentInward(warehouseId));
        }
        if (anyOf(lower, "outward", "dispatch", "delivery")) {
            sections.add(fetchRecentOutward(warehouseId));
        }
        if (anyOf(lower, "bond", "pledge", "lien", "collateral")) {
            sections.add(fetchBondSummary(warehouseId));
        }
        if (anyOf(lower, "gate", "vehicle", "driver", "weighbridge")) {
            sections.add(fetchGateStatus(warehouseId));
        }

        if (sections.isEmpty()) {
            return ""; // No specific context — let Ollama answer from training
        }

        String context = String.join("\n\n", sections);
        // Hard cap at 500 tokens ≈ 2000 chars
        if (context.length() > 2000) {
            context = context.substring(0, 2000) + "\n... [context truncated]";
        }

        log.debug("Context block:\n{}", context);
        return "═══ LIVE WAREHOUSE CONTEXT ═══\n" + context + "\n═══════════════════════════════";
    }

    // ─── DB Fetchers ──────────────────────────────────────────────────────────

    private String fetchInventorySummary(String warehouseId) {
        if (jdbc == null) return "INVENTORY: DB not connected (no live data available)";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT commodity_name, SUM(current_qty) AS total_qty, unit
                FROM inventory_items
                WHERE warehouse_id = ?
                GROUP BY commodity_name, unit
                ORDER BY total_qty DESC
                LIMIT 10
                """, warehouseId);
            StringBuilder sb = new StringBuilder("INVENTORY SUMMARY:\n");
            for (Map<String, Object> r : rows) {
                sb.append(String.format("  %s: %s %s\n",
                    r.get("commodity_name"), r.get("total_qty"), r.get("unit")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("fetchInventorySummary failed: {}", e.getMessage());
            return "INVENTORY: data temporarily unavailable";
        }
    }

    private String fetchRecentInward(String warehouseId) {
        if (jdbc == null) return "INWARD: DB not connected (no live data available)";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT receipt_no, party_name, commodity, bags, status, created_at
                FROM inward_receipts
                WHERE warehouse_id = ?
                ORDER BY created_at DESC
                LIMIT 5
                """, warehouseId);
            StringBuilder sb = new StringBuilder("RECENT INWARD RECEIPTS:\n");
            for (Map<String, Object> r : rows) {
                sb.append(String.format("  #%s | %s | %s | %s bags | %s\n",
                    r.get("receipt_no"), r.get("party_name"),
                    r.get("commodity"), r.get("bags"), r.get("status")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("fetchRecentInward failed: {}", e.getMessage());
            return "INWARD: data temporarily unavailable";
        }
    }

    private String fetchRecentOutward(String warehouseId) {
        if (jdbc == null) return "OUTWARD: DB not connected (no live data available)";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT dispatch_no, party_name, commodity, bags, status, created_at
                FROM outward_dispatches
                WHERE warehouse_id = ?
                ORDER BY created_at DESC
                LIMIT 5
                """, warehouseId);
            StringBuilder sb = new StringBuilder("RECENT OUTWARD DISPATCHES:\n");
            for (Map<String, Object> r : rows) {
                sb.append(String.format("  #%s | %s | %s | %s bags | %s\n",
                    r.get("dispatch_no"), r.get("party_name"),
                    r.get("commodity"), r.get("bags"), r.get("status")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("fetchRecentOutward failed: {}", e.getMessage());
            return "OUTWARD: data temporarily unavailable";
        }
    }

    private String fetchBondSummary(String warehouseId) {
        if (jdbc == null) return "BONDS: DB not connected (no live data available)";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT bond_no, party_name, commodity, collateral_value,
                       expiry_date, status
                FROM bonds
                WHERE warehouse_id = ? AND status = 'ACTIVE'
                ORDER BY expiry_date ASC
                LIMIT 5
                """, warehouseId);
            StringBuilder sb = new StringBuilder("ACTIVE BONDS:\n");
            for (Map<String, Object> r : rows) {
                sb.append(String.format("  #%s | %s | %s | ₹%s | Expires: %s\n",
                    r.get("bond_no"), r.get("party_name"),
                    r.get("commodity"), r.get("collateral_value"),
                    r.get("expiry_date")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("fetchBondSummary failed: {}", e.getMessage());
            return "BONDS: data temporarily unavailable";
        }
    }

    private String fetchGateStatus(String warehouseId) {
        if (jdbc == null) return "GATE: DB not connected (no live data available)";
        try {
            Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gate_passes
                WHERE warehouse_id = ? AND status = 'ACTIVE'
                """, Integer.class, warehouseId);
            return String.format("GATE STATUS: %d vehicles currently inside", active);
        } catch (Exception e) {
            log.warn("fetchGateStatus failed: {}", e.getMessage());
            return "GATE: data temporarily unavailable";
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean anyOf(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
