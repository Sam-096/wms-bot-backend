package com.wnsai.wms_bot.dto.dashboard;

public record SnapshotResponse(
    int    stockHealthPercent,
    long   activeVehicles,
    long   pendingInward,
    long   pendingOutward,
    int    lowStockCount,
    long   activeBonds,
    String lastUpdated
) {}
