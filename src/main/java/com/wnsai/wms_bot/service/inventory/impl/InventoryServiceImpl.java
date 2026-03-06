package com.wnsai.wms_bot.service.inventory.impl;

import com.wnsai.wms_bot.dto.inventory.InventoryResponse;
import com.wnsai.wms_bot.dto.inventory.InventorySummaryResponse;
import com.wnsai.wms_bot.dto.inventory.InventoryUpdateRequest;
import com.wnsai.wms_bot.entity.StockInventory;
import com.wnsai.wms_bot.exception.EntityNotFoundException;
import com.wnsai.wms_bot.mapper.InventoryMapper;
import com.wnsai.wms_bot.repository.StockInventoryRepository;
import com.wnsai.wms_bot.service.inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final StockInventoryRepository stockRepo;

    @Override
    public Mono<Page<InventoryResponse>> list(String warehouseId, int page, int size) {
        return Mono.<Page<InventoryResponse>>fromCallable(() -> {
            PageRequest pr = PageRequest.of(page, size, Sort.by("itemName").ascending());
            List<StockInventory> all = stockRepo.findByWarehouseId(warehouseId);
            int start = (int) pr.getOffset();
            int end   = Math.min(start + size, all.size());
            List<InventoryResponse> content = all.subList(start, end)
                    .stream().map(InventoryMapper::toResponse).collect(Collectors.toList());
            return new PageImpl<>(content, pr, all.size());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<InventoryResponse>> getLowStock(String warehouseId) {
        return Mono.fromCallable(() ->
            stockRepo.findLowStockItems(warehouseId)
                    .stream().map(InventoryMapper::toResponse).collect(Collectors.toList())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<InventoryResponse> getById(UUID id) {
        return Mono.fromCallable(() -> {
            StockInventory stock = stockRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("StockInventory", id));
            return InventoryMapper.toResponse(stock);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<InventoryResponse> update(UUID id, InventoryUpdateRequest request) {
        return Mono.fromCallable(() -> {
            StockInventory stock = stockRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("StockInventory", id));
            if (request.itemCode()    != null) stock.setItemCode(request.itemCode());
            if (request.minThreshold() != null) stock.setMinThreshold(request.minThreshold());
            if (request.unit()        != null) stock.setUnit(request.unit());
            stock = stockRepo.save(stock);
            return InventoryMapper.toResponse(stock);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<InventorySummaryResponse> getSummary(String warehouseId) {
        return Mono.fromCallable(() -> {
            List<StockInventory> all      = stockRepo.findByWarehouseId(warehouseId);
            List<StockInventory> lowStock = stockRepo.findLowStockItems(warehouseId);
            int total    = all.size();
            int lowCount = lowStock.size();
            int health   = total > 0 ? (int) (((total - lowCount) * 100.0) / total) : 100;
            return new InventorySummaryResponse(total, lowCount, health, warehouseId);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
