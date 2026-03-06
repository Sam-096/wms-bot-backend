package com.wnsai.wms_bot.chat;

import com.wnsai.wms_bot.entity.*;
import com.wnsai.wms_bot.intent.IntentResult;
import com.wnsai.wms_bot.service.WarehouseDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles QUICK_QUERY intents by fetching data directly from the database
 * and returning structured ChatResponse objects (TABLE / LIST / INSTANT).
 *
 * Keyword matching is done on the raw message after IntentClassifier
 * already determined this is a QUICK_QUERY — so checks here are lightweight.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseQueryHandler {

    private final WarehouseDataService warehouseDataService;

    /**
     * Dispatches to the appropriate query based on message keywords.
     * Falls back to a generic INSTANT text response if no match.
     */
    public Mono<ChatResponse> handle(ChatRequest req, IntentResult intent) {
        return Mono.fromCallable(() -> dispatch(req, intent))
                   .subscribeOn(Schedulers.boundedElastic());
    }

    // ─── Internal dispatch ────────────────────────────────────────────────────

    private ChatResponse dispatch(ChatRequest req, IntentResult intent) {
        String msg   = req.message().toLowerCase();
        String whId  = req.warehouseId();

        // Low stock / stock alerts
        if (contains(msg, "low stock", "stock alert", "low inventory", "stock warning",
                         "कम स्टॉक", "తక్కువ స్టాక్")) {
            return handleLowStock(whId, intent);
        }

        // Open gate passes
        if (contains(msg, "gate pass", "vehicle", "open pass", "गेट पास", "గేట్ పాస్")) {
            return handleOpenGatePasses(whId, intent);
        }

        // Active bonds / expiring bonds
        if (contains(msg, "bond", "expir", "बॉंड", "బాండ్")) {
            return handleBonds(whId, intent);
        }

        // Pending inward
        if (contains(msg, "pending inward", "incoming", "grn", "receipt",
                         "पेंडिंग", "పెండింగ్ ఇన్వార్డ్")) {
            return handlePendingCounts(whId, intent);
        }

        // Generic: recent chat activity
        if (contains(msg, "recent", "last", "history")) {
            return handleRecentActivity(whId, intent);
        }

        // Unmatched — let caller fall back to LLM
        return null;
    }

    // ─── Query handlers ───────────────────────────────────────────────────────

    private ChatResponse handleLowStock(String warehouseId, IntentResult intent) {
        List<StockInventory> items = warehouseDataService.getLowStockItems(warehouseId);

        if (items.isEmpty()) {
            return ChatResponse.instant(
                "✅ All stock levels are healthy. No items below threshold.",
                intent.type().name(), null, "RULE_BASED",
                List.of("Show full stock summary", "Check bond status", "View recent inward")
            );
        }

        List<Map<String, Object>> rows = items.stream()
            .map(s -> Map.<String, Object>of(
                "item",      s.getItemName(),
                "code",      s.getItemCode() != null ? s.getItemCode() : "-",
                "current",   s.getCurrentStock(),
                "threshold", s.getMinThreshold(),
                "unit",      s.getUnit() != null ? s.getUnit() : "-"
            ))
            .collect(Collectors.toList());

        return ChatResponse.table(
            Map.of("columns", List.of("Item", "Code", "Current Stock", "Min Threshold", "Unit"),
                   "rows",    rows,
                   "summary", items.size() + " item(s) below minimum threshold"),
            intent.type().name(),
            List.of("Generate low-stock CSV report", "View all stock", "Who manages these items?")
        );
    }

    private ChatResponse handleOpenGatePasses(String warehouseId, IntentResult intent) {
        List<GatePass> passes = warehouseDataService.getOpenGatePasses(warehouseId);

        if (passes.isEmpty()) {
            return ChatResponse.instant(
                "No open gate passes at the moment.",
                intent.type().name(), null, "RULE_BASED",
                List.of("View gate pass history", "Create new gate pass")
            );
        }

        List<Map<String, Object>> rows = passes.stream()
            .map(g -> Map.<String, Object>of(
                "pass",    g.getPassNumber() != null ? g.getPassNumber() : "-",
                "vehicle", g.getVehicleNumber() != null ? g.getVehicleNumber() : "-",
                "driver",  g.getDriverName() != null ? g.getDriverName() : "-",
                "entry",   g.getEntryTime() != null ? g.getEntryTime().toString() : "-"
            ))
            .collect(Collectors.toList());

        return ChatResponse.table(
            Map.of("columns", List.of("Pass #", "Vehicle", "Driver", "Entry Time"),
                   "rows",    rows,
                   "summary", passes.size() + " vehicle(s) currently inside"),
            intent.type().name(),
            List.of("Check for overstaying vehicles", "Close a gate pass", "Export gate pass log")
        );
    }

    private ChatResponse handleBonds(String warehouseId, IntentResult intent) {
        List<Bond> expiring = warehouseDataService.getExpiringBonds(warehouseId, 30);
        List<Bond> active   = warehouseDataService.getActiveBonds(warehouseId);

        if (expiring.isEmpty()) {
            return ChatResponse.instant(
                "No bonds expiring in the next 30 days. Total active bonds: " + active.size(),
                intent.type().name(), null, "RULE_BASED",
                List.of("View all active bonds", "Generate bond status report")
            );
        }

        List<Map<String, Object>> rows = expiring.stream()
            .map(b -> Map.<String, Object>of(
                "bond",    b.getBondNumber() != null ? b.getBondNumber() : "-",
                "item",    b.getItemName() != null ? b.getItemName() : "-",
                "qty",     b.getQuantity() != null ? b.getQuantity() : "-",
                "expiry",  b.getExpiryDate() != null ? b.getExpiryDate().toString() : "-",
                "status",  b.getStatus() != null ? b.getStatus() : "-"
            ))
            .collect(Collectors.toList());

        return ChatResponse.table(
            Map.of("columns", List.of("Bond #", "Item", "Qty", "Expiry Date", "Status"),
                   "rows",    rows,
                   "summary", expiring.size() + " bond(s) expiring within 30 days"),
            intent.type().name(),
            List.of("Generate bond status report", "Release a bond", "View all active bonds")
        );
    }

    private ChatResponse handlePendingCounts(String warehouseId, IntentResult intent) {
        long pendingIn  = warehouseDataService.getPendingInwardCount(warehouseId);
        long pendingOut = warehouseDataService.getPendingOutwardCount(warehouseId);

        String text = String.format(
            "📦 Pending Inward: %d  |  📤 Pending Outward: %d", pendingIn, pendingOut);

        return ChatResponse.instant(
            text, intent.type().name(), null, "RULE_BASED",
            List.of("View pending inward list", "Approve inward transactions",
                    "Generate inward summary report")
        );
    }

    private ChatResponse handleRecentActivity(String warehouseId, IntentResult intent) {
        var msgs = warehouseDataService.getRecentChats(warehouseId);
        if (msgs.isEmpty()) {
            return ChatResponse.instant(
                "No recent activity found for this warehouse.",
                intent.type().name(), null, "RULE_BASED", List.of()
            );
        }

        List<Map<String, Object>> rows = msgs.stream()
            .map(m -> Map.<String, Object>of(
                "time",   m.getCreatedAt() != null ? m.getCreatedAt().toLocalTime().toString() : "-",
                "intent", m.getIntent() != null ? m.getIntent() : "-",
                "query",  m.getUserMessage() != null
                              ? (m.getUserMessage().length() > 60
                                    ? m.getUserMessage().substring(0, 60) + "…"
                                    : m.getUserMessage())
                              : "-"
            ))
            .collect(Collectors.toList());

        return ChatResponse.list(
            Map.of("columns", List.of("Time", "Intent", "Query"),
                   "rows",    rows),
            intent.type().name(),
            List.of("Show full chat history")
        );
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    private boolean contains(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
