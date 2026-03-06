package com.wnsai.wms_bot.service.inward.impl;

import com.wnsai.wms_bot.dto.inward.InwardRequest;
import com.wnsai.wms_bot.dto.inward.InwardResponse;
import com.wnsai.wms_bot.entity.InwardTransaction;
import com.wnsai.wms_bot.entity.StockInventory;
import com.wnsai.wms_bot.exception.EntityNotFoundException;
import com.wnsai.wms_bot.mapper.InwardMapper;
import com.wnsai.wms_bot.repository.InwardTransactionRepository;
import com.wnsai.wms_bot.repository.StockInventoryRepository;
import com.wnsai.wms_bot.service.inward.InwardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InwardServiceImpl implements InwardService {

    private final InwardTransactionRepository inwardRepo;
    private final StockInventoryRepository    stockRepo;

    @Override
    public Mono<Page<InwardResponse>> list(String warehouseId, String status,
                                            LocalDate dateFrom, LocalDate dateTo,
                                            int page, int size) {
        return Mono.fromCallable(() -> {
            log.debug("list inward warehouseId={} status={}", warehouseId, status);
            PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<InwardTransaction> result = inwardRepo.findFiltered(warehouseId, status, dateFrom, dateTo, pr);
            return result.map(InwardMapper::toResponse);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<InwardResponse> getById(UUID id) {
        return Mono.fromCallable(() -> {
            log.debug("getById inward id={}", id);
            InwardTransaction tx = inwardRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("InwardTransaction", id));
            return InwardMapper.toResponse(tx);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<InwardResponse> create(InwardRequest request) {
        return Mono.fromCallable(() -> {
            log.debug("create inward warehouseId={}", request.warehouseId());
            InwardTransaction tx = InwardMapper.toEntity(request);
            tx = inwardRepo.save(tx);
            log.info("InwardTransaction created id={}", tx.getId());
            return InwardMapper.toResponse(tx);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<InwardResponse> update(UUID id, InwardRequest request) {
        return Mono.fromCallable(() -> {
            log.debug("update inward id={}", id);
            InwardTransaction tx = inwardRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("InwardTransaction", id));

            tx.setItemName(request.commodityName());
            tx.setSupplierName(request.supplierName());
            tx.setVehicleNumber(request.vehicleNumber());
            tx.setQuantityBags(request.quantityBags());
            if (request.quantityBags() != null && request.unitWeight() != null) {
                tx.setUnitWeight(request.unitWeight());
                tx.setTotalWeight(request.unitWeight().multiply(BigDecimal.valueOf(request.quantityBags())));
            }
            tx.setUnit(request.unit());
            tx.setRemarks(request.remarks());
            if (request.inwardDate() != null) tx.setInwardDate(request.inwardDate());

            tx = inwardRepo.save(tx);
            return InwardMapper.toResponse(tx);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<InwardResponse> approve(UUID id, String approvedByUserId) {
        return Mono.fromCallable(() -> {
            log.debug("approve inward id={} by={}", id, approvedByUserId);
            InwardTransaction tx = inwardRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("InwardTransaction", id));

            UUID approvedBy = UUID.fromString(approvedByUserId);
            inwardRepo.updateApproval(id, "APPROVED", approvedBy);

            // Update stock inventory
            updateStock(tx.getWarehouseId(), tx.getItemName(),
                    tx.getQuantityBags() != null ? BigDecimal.valueOf(tx.getQuantityBags()) : tx.getQuantity(),
                    tx.getUnit(),
                    true);

            tx.setStatus("APPROVED");
            tx.setApprovedBy(approvedBy);
            return InwardMapper.toResponse(tx);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<InwardResponse> reject(UUID id, String reason) {
        return Mono.fromCallable(() -> {
            log.debug("reject inward id={}", id);
            InwardTransaction tx = inwardRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("InwardTransaction", id));
            tx.setStatus("REJECTED");
            tx.setRemarks(reason);
            tx = inwardRepo.save(tx);
            return InwardMapper.toResponse(tx);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return Mono.fromRunnable(() -> {
            log.debug("delete inward id={}", id);
            if (!inwardRepo.existsById(id)) {
                throw new EntityNotFoundException("InwardTransaction", id);
            }
            inwardRepo.deleteById(id);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private void updateStock(String warehouseId, String itemName,
                              BigDecimal qty, String unit, boolean add) {
        try {
            List<StockInventory> items = stockRepo.findByWarehouseId(warehouseId);
            StockInventory stock = items.stream()
                    .filter(s -> s.getItemName().equalsIgnoreCase(itemName))
                    .findFirst()
                    .orElse(null);

            if (stock == null) {
                if (add) {
                    stock = StockInventory.builder()
                            .warehouseId(warehouseId)
                            .itemName(itemName)
                            .currentStock(qty)
                            .minThreshold(BigDecimal.ZERO)
                            .unit(unit)
                            .build();
                    stockRepo.save(stock);
                    log.info("New stock item created: {} in {}", itemName, warehouseId);
                }
            } else {
                BigDecimal newQty = add
                        ? stock.getCurrentStock().add(qty)
                        : stock.getCurrentStock().subtract(qty);
                stock.setCurrentStock(newQty.max(BigDecimal.ZERO));
                stockRepo.save(stock);
                log.info("Stock updated: {} -> {}", itemName, stock.getCurrentStock());
            }
        } catch (Exception e) {
            log.error("Stock update failed for item={}: {}", itemName, e.getMessage());
        }
    }
}
