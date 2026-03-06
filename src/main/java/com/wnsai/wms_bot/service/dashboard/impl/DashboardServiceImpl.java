package com.wnsai.wms_bot.service.dashboard.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wnsai.wms_bot.dto.dashboard.SnapshotResponse;
import com.wnsai.wms_bot.repository.*;
import com.wnsai.wms_bot.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final InwardTransactionRepository  inwardRepo;
    private final OutwardTransactionRepository outwardRepo;
    private final StockInventoryRepository     stockRepo;
    private final GatePassRepository           gatePassRepo;
    private final BondRepository               bondRepo;

    /** 60-second TTL Caffeine cache. Key: warehouseId */
    private final Cache<String, SnapshotResponse> snapshotCache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(50)
            .build();

    @Override
    public Mono<SnapshotResponse> getSnapshot(String warehouseId) {
        // Return cached if fresh
        SnapshotResponse cached = snapshotCache.getIfPresent(warehouseId);
        if (cached != null) {
            log.debug("Dashboard snapshot cache hit: warehouseId={}", warehouseId);
            return Mono.just(cached);
        }

        // All DB queries in parallel on boundedElastic
        Mono<Long> pendingInward  = Mono.fromCallable(
                () -> inwardRepo.countByWarehouseIdAndStatus(warehouseId, "PENDING"))
                .subscribeOn(Schedulers.boundedElastic());

        Mono<Long> pendingOutward = Mono.fromCallable(
                () -> outwardRepo.countByWarehouseIdAndStatus(warehouseId, "PENDING"))
                .subscribeOn(Schedulers.boundedElastic());

        Mono<Long> activeVehicles = Mono.fromCallable(
                () -> (long) gatePassRepo.findActiveByWarehouseId(warehouseId).size())
                .subscribeOn(Schedulers.boundedElastic());

        Mono<Long> activeBonds = Mono.fromCallable(
                () -> bondRepo.countByWarehouseIdAndStatus(warehouseId, "ACTIVE"))
                .subscribeOn(Schedulers.boundedElastic());

        Mono<Integer> lowStockCount = Mono.fromCallable(
                () -> stockRepo.findLowStockItems(warehouseId).size())
                .subscribeOn(Schedulers.boundedElastic());

        Mono<Integer> totalItems = Mono.fromCallable(
                () -> stockRepo.findByWarehouseId(warehouseId).size())
                .subscribeOn(Schedulers.boundedElastic());

        return Mono.zip(pendingInward, pendingOutward, activeVehicles, activeBonds, lowStockCount, totalItems)
                .map(tuple -> {
                    long  pi   = tuple.getT1();
                    long  po   = tuple.getT2();
                    long  av   = tuple.getT3();
                    long  ab   = tuple.getT4();
                    int   low  = tuple.getT5();
                    int   total = tuple.getT6();
                    int   health = total > 0 ? (int)(((total - low) * 100.0) / total) : 100;

                    String ts = OffsetDateTime.now(ZoneOffset.UTC)
                            .format(DateTimeFormatter.ISO_DATE_TIME);

                    SnapshotResponse snap = new SnapshotResponse(health, av, pi, po, low, ab, ts);
                    snapshotCache.put(warehouseId, snap);
                    log.debug("Dashboard snapshot computed: warehouseId={}", warehouseId);
                    return snap;
                });
    }
}
