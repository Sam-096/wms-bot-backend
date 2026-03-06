package com.wnsai.wms_bot.navigation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps natural language (any Indian language + English) to Angular routes.
 * Pure in-memory — target < 5ms.
 */
@Slf4j
@Service
public class NavigationService implements NavigationResolver {

    // Ordered: longer/more specific phrases first to avoid prefix conflicts
    private static final Map<String, NavigationCommand> ROUTE_MAP = new LinkedHashMap<>();

    static {
        ROUTE_MAP.put("new inward",   new NavigationCommand("/inward/new",   "New Inward Receipt"));
        ROUTE_MAP.put("new outward",  new NavigationCommand("/outward/new",  "New Outward Dispatch"));
        ROUTE_MAP.put("gate pass",    new NavigationCommand("/gate-pass",    "Gate Pass"));
        ROUTE_MAP.put("gate entry",   new NavigationCommand("/gate-pass",    "Gate Entry"));
        ROUTE_MAP.put("dashboard",    new NavigationCommand("/dashboard",    "Dashboard"));
        ROUTE_MAP.put("inventory",    new NavigationCommand("/inventory",    "Inventory"));
        ROUTE_MAP.put("inward",       new NavigationCommand("/inward",       "Inward Receipts"));
        ROUTE_MAP.put("outward",      new NavigationCommand("/outward",      "Outward Dispatch"));
        ROUTE_MAP.put("bonds",        new NavigationCommand("/bonds",        "Bonds"));
        ROUTE_MAP.put("bond",         new NavigationCommand("/bonds",        "Bonds"));
        ROUTE_MAP.put("reports",      new NavigationCommand("/reports",      "Reports"));
        ROUTE_MAP.put("report",       new NavigationCommand("/reports",      "Reports"));
        ROUTE_MAP.put("finance",      new NavigationCommand("/finance",      "Finance"));
        ROUTE_MAP.put("invoice",      new NavigationCommand("/finance/invoices", "Invoices"));
        ROUTE_MAP.put("settings",     new NavigationCommand("/settings",     "Settings"));
        ROUTE_MAP.put("users",        new NavigationCommand("/settings/users", "User Management"));
        ROUTE_MAP.put("qc",           new NavigationCommand("/qc",           "Quality Control"));
        ROUTE_MAP.put("quality",      new NavigationCommand("/qc",           "Quality Control"));

        // Telugu phrases
        ROUTE_MAP.put("inward పేజీ",  new NavigationCommand("/inward",       "Inward Receipts"));
        ROUTE_MAP.put("outward పేజీ", new NavigationCommand("/outward",      "Outward Dispatch"));
        ROUTE_MAP.put("inventory పేజీ", new NavigationCommand("/inventory",  "Inventory"));
    }

    @Override
    public Optional<NavigationCommand> resolve(String message) {
        if (message == null || message.isBlank()) return Optional.empty();

        String lower = message.toLowerCase().trim();
        long start = System.currentTimeMillis();

        for (Map.Entry<String, NavigationCommand> entry : ROUTE_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                NavigationCommand cmd = entry.getValue();
                log.info("Navigation resolved: '{}' → {} in {}ms",
                    truncate(message), cmd.route(), System.currentTimeMillis() - start);
                return Optional.of(cmd);
            }
        }

        return Optional.empty();
    }

    private String truncate(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
