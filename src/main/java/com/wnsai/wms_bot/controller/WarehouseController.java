package com.wnsai.wms_bot.controller;

import com.wnsai.wms_bot.entity.Warehouse;
import com.wnsai.wms_bot.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * GET /api/v1/warehouses          — all active warehouses
 * GET /api/v1/warehouses/{id}     — single warehouse by warehouseId string
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseRepository warehouseRepo;

    @GetMapping
    public Mono<List<Warehouse>> listActive() {
        return Mono.fromCallable(warehouseRepo::findByIsActiveTrue)
                   .subscribeOn(Schedulers.boundedElastic())
                   .doOnSuccess(ws -> log.debug("GET /api/v1/warehouses returned {} records", ws.size()));
    }

    @GetMapping("/{warehouseId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPERATOR','GATE_STAFF','VIEWER')")
    public Mono<Warehouse> getOne(@PathVariable String warehouseId) {
        return Mono.fromCallable(() ->
            warehouseRepo.findByWarehouseId(warehouseId)
                .orElseThrow(() -> new com.wnsai.wms_bot.exception.EntityNotFoundException(
                    "Warehouse", warehouseId))
        ).subscribeOn(Schedulers.boundedElastic());
    }
}
