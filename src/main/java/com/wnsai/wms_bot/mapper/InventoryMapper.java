package com.wnsai.wms_bot.mapper;

import com.wnsai.wms_bot.dto.inventory.InventoryResponse;
import com.wnsai.wms_bot.entity.StockInventory;

public final class InventoryMapper {

    private InventoryMapper() {}

    public static InventoryResponse toResponse(StockInventory e) {
        boolean lowStock = e.getCurrentStock() != null
                && e.getMinThreshold() != null
                && e.getCurrentStock().compareTo(e.getMinThreshold()) <= 0;
        return new InventoryResponse(
                e.getId(),
                e.getWarehouseId(),
                e.getItemName(),
                e.getItemCode(),
                e.getCurrentStock(),
                e.getMinThreshold(),
                e.getUnit(),
                lowStock,
                e.getLastUpdated()
        );
    }
}
