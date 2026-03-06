package com.wnsai.wms_bot.dto.inventory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryResponse(
    UUID           id,
    String         warehouseId,
    String         itemName,
    String         itemCode,
    BigDecimal     currentStock,
    BigDecimal     minThreshold,
    String         unit,
    boolean        isLowStock,
    OffsetDateTime lastUpdated
) {}
