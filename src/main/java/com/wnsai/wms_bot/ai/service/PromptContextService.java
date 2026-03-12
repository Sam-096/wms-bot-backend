package com.wnsai.wms_bot.ai.service;

import com.wnsai.wms_bot.ai.model.LiveWarehouseContext;
import com.wnsai.wms_bot.ai.model.LiveWarehouseContext.StockEntry;
import com.wnsai.wms_bot.repository.GatePassRepository;
import com.wnsai.wms_bot.repository.InwardTransactionRepository;
import com.wnsai.wms_bot.repository.OutwardTransactionRepository;
import com.wnsai.wms_bot.repository.StockInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;

/**
 * Fetches a live snapshot of warehouse state for AI system prompt injection.
 *
 * All JPA calls are blocking — wrapped in Mono.fromCallable + boundedElastic.
 * Returns LiveWarehouseContext.empty() on any failure so the prompt pipeline
 * never blocks even if the DB is slow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptContextService {

    private final GatePassRepository            gatePassRepo;
    private final InwardTransactionRepository   inwardRepo;
    private final OutwardTransactionRepository  outwardRepo;
    private final StockInventoryRepository      stockRepo;

    /**
     * Fetches all live stats in a single boundedElastic thread.
     * Typical latency: 20-50ms (4 simple queries).
     */
    public Mono<LiveWarehouseContext> fetchContext(String warehouseId) {
        if (warehouseId == null || warehouseId.isBlank()) {
            return Mono.just(LiveWarehouseContext.empty());
        }

        return Mono.fromCallable(() -> buildContext(warehouseId))
                   .subscribeOn(Schedulers.boundedElastic())
                   .onErrorResume(e -> {
                       log.warn("PromptContextService failed for wh={}: {} — using empty context",
                               warehouseId, e.getMessage());
                       return Mono.just(LiveWarehouseContext.empty());
                   });
    }

    // ─── Private ──────────────────────────────────────────────────────────────

    private LiveWarehouseContext buildContext(String warehouseId) {
        int vehiclesInside   = gatePassRepo.findActiveByWarehouseId(warehouseId).size();
        int pendingInward    = (int) inwardRepo.countByWarehouseIdAndStatus(warehouseId, "PENDING");
        int todayDispatched  = (int) outwardRepo.countTodayDispatched(warehouseId, LocalDate.now());
        List<StockEntry> low = stockRepo.findLowStockItems(warehouseId)
                .stream()
                .limit(10) // cap at 10 for prompt size
                .map(s -> new StockEntry(
                        s.getItemName(),
                        s.getCurrentStock() != null ? s.getCurrentStock().toPlainString() : "0",
                        s.getMinThreshold() != null ? s.getMinThreshold().toPlainString() : "0",
                        s.getUnit() != null ? s.getUnit() : "units"))
                .toList();

        log.debug("LiveContext wh={}: vehicles={} pendingIn={} todayOut={} lowStock={}",
                warehouseId, vehiclesInside, pendingInward, todayDispatched, low.size());

        return new LiveWarehouseContext(vehiclesInside, pendingInward, todayDispatched, low);
    }
}
