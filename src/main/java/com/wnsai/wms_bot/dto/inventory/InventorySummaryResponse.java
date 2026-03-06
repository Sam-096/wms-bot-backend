package com.wnsai.wms_bot.dto.inventory;

public record InventorySummaryResponse(
    int    totalItems,
    int    lowStockCount,
    int    healthPercent,
    String warehouseId
) {}
