package com.wnsai.wms_bot.service;

import com.wnsai.wms_bot.entity.*;
import com.wnsai.wms_bot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Single service exposing read/write operations on WMS warehouse data.
 *
 * Rules:
 *  - Every method is try-catch; never propagates exceptions to callers.
 *  - Returns empty List / 0 / null on failure so the chat pipeline keeps running.
 *  - All JPA calls are blocking; callers in WebFlux must wrap with
 *    Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic()).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseDataService {

    private final InwardTransactionRepository  inwardRepo;
    private final OutwardTransactionRepository outwardRepo;
    private final StockInventoryRepository     stockRepo;
    private final GatePassRepository           gatePassRepo;
    private final BondRepository               bondRepo;
    private final ChatMessageRepository        chatMessageRepo;

    // ─── Counts ───────────────────────────────────────────────────────────────

    public long getPendingInwardCount(String warehouseId) {
        try {
            return inwardRepo.countByWarehouseIdAndStatus(warehouseId, "PENDING");
        } catch (Exception e) {
            log.error("getPendingInwardCount failed for warehouseId={}: {}", warehouseId, e.getMessage());
            return 0L;
        }
    }

    public long getPendingOutwardCount(String warehouseId) {
        try {
            return outwardRepo.countByWarehouseIdAndStatus(warehouseId, "PENDING");
        } catch (Exception e) {
            log.error("getPendingOutwardCount failed for warehouseId={}: {}", warehouseId, e.getMessage());
            return 0L;
        }
    }

    // ─── Lists ────────────────────────────────────────────────────────────────

    public List<StockInventory> getLowStockItems(String warehouseId) {
        try {
            return stockRepo.findLowStockItems(warehouseId);
        } catch (Exception e) {
            log.error("getLowStockItems failed for warehouseId={}: {}", warehouseId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<GatePass> getOpenGatePasses(String warehouseId) {
        try {
            return gatePassRepo.findByWarehouseIdAndStatus(warehouseId, "OPEN");
        } catch (Exception e) {
            log.error("getOpenGatePasses failed for warehouseId={}: {}", warehouseId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Bond> getActiveBonds(String warehouseId) {
        try {
            return bondRepo.findByWarehouseIdAndStatus(warehouseId, "ACTIVE");
        } catch (Exception e) {
            log.error("getActiveBonds failed for warehouseId={}: {}", warehouseId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Bond> getExpiringBonds(String warehouseId, int withinDays) {
        try {
            LocalDate from = LocalDate.now();
            LocalDate to   = from.plusDays(withinDays);
            return bondRepo.findExpiringBonds(warehouseId, from, to);
        } catch (Exception e) {
            log.error("getExpiringBonds failed for warehouseId={}: {}", warehouseId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─── Chat persistence ─────────────────────────────────────────────────────

    /**
     * Persists a chat exchange to the chat_messages table.
     * Returns the saved entity (with generated ID), or null on failure.
     */
    public ChatMessage saveChatMessage(ChatMessage msg) {
        try {
            ChatMessage saved = chatMessageRepo.save(msg);
            log.info("ChatMessage saved id={} sessionId={} intent={}",
                saved.getId(), saved.getSessionId(), saved.getIntent());
            return saved;
        } catch (Exception e) {
            log.error("saveChatMessage failed for sessionId={}: {}", msg.getSessionId(), e.getMessage());
            return null;
        }
    }

    public List<ChatMessage> getRecentChats(String warehouseId) {
        try {
            return chatMessageRepo.findTop10ByWarehouseIdOrderByCreatedAtDesc(warehouseId);
        } catch (Exception e) {
            log.error("getRecentChats failed for warehouseId={}: {}", warehouseId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
