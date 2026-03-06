package com.wnsai.wms_bot.service.inventory;

import com.wnsai.wms_bot.dto.inventory.InventoryResponse;
import com.wnsai.wms_bot.dto.inventory.InventorySummaryResponse;
import com.wnsai.wms_bot.dto.inventory.InventoryUpdateRequest;
import org.springframework.data.domain.Page;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface InventoryService {
    Mono<Page<InventoryResponse>>   list(String warehouseId, int page, int size);
    Mono<List<InventoryResponse>>   getLowStock(String warehouseId);
    Mono<InventoryResponse>         getById(UUID id);
    Mono<InventoryResponse>         update(UUID id, InventoryUpdateRequest request);
    Mono<InventorySummaryResponse>  getSummary(String warehouseId);
}
