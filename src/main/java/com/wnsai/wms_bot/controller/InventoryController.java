package com.wnsai.wms_bot.controller;

import com.wnsai.wms_bot.dto.inventory.InventoryResponse;
import com.wnsai.wms_bot.dto.inventory.InventorySummaryResponse;
import com.wnsai.wms_bot.dto.inventory.InventoryUpdateRequest;
import com.wnsai.wms_bot.service.inventory.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','VIEWER')")
    public Mono<Page<InventoryResponse>> list(
            @RequestParam                     String warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventoryService.list(warehouseId, page, size);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','VIEWER')")
    public Mono<List<InventoryResponse>> getLowStock(@RequestParam String warehouseId) {
        return inventoryService.getLowStock(warehouseId);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','VIEWER')")
    public Mono<InventorySummaryResponse> getSummary(@RequestParam String warehouseId) {
        return inventoryService.getSummary(warehouseId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','VIEWER')")
    public Mono<InventoryResponse> getById(@PathVariable UUID id) {
        return inventoryService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<InventoryResponse> update(@PathVariable UUID id,
                                           @Valid @RequestBody InventoryUpdateRequest request) {
        return inventoryService.update(id, request);
    }
}
