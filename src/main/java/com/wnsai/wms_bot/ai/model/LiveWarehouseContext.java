package com.wnsai.wms_bot.ai.model;

import java.util.List;

/**
 * Snapshot of live warehouse state injected into every AI system prompt.
 * Fetched once per chat request from PromptContextService.
 */
public record LiveWarehouseContext(
    int              vehiclesInside,
    int              pendingInward,
    int              todayDispatched,
    List<StockEntry> lowStockItems
) {

    /** Minimal summary of a low-stock inventory item. */
    public record StockEntry(String name, String current, String threshold, String unit) {}

    /** Returned when warehouseId is missing or DB call fails — prevents NPE downstream. */
    public static LiveWarehouseContext empty() {
        return new LiveWarehouseContext(0, 0, 0, List.of());
    }

    public boolean isEmpty() {
        return vehiclesInside == 0 && pendingInward == 0
            && todayDispatched == 0 && lowStockItems.isEmpty();
    }
}
