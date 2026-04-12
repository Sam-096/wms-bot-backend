package com.wnsai.wms_bot.service.outward.impl;

import com.wnsai.wms_bot.dto.outward.OutwardRequest;
import com.wnsai.wms_bot.dto.outward.OutwardResponse;
import com.wnsai.wms_bot.entity.OutwardTransaction;
import com.wnsai.wms_bot.entity.StockInventory;
import com.wnsai.wms_bot.exception.EntityNotFoundException;
import com.wnsai.wms_bot.exception.InsufficientStockException;
import com.wnsai.wms_bot.mapper.OutwardMapper;
import com.wnsai.wms_bot.repository.OutwardTransactionRepository;
import com.wnsai.wms_bot.repository.StockInventoryRepository;
import com.wnsai.wms_bot.service.outward.OutwardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutwardServiceImpl implements OutwardService {

    private final OutwardTransactionRepository outwardRepo;
    private final StockInventoryRepository     stockRepo;
    private final TransactionTemplate          txTemplate;

    @Override
    public Mono<Page<OutwardResponse>> list(String warehouseId, String status,
                                             LocalDate dateFrom, LocalDate dateTo,
                                             int page, int size) {
        return Mono.fromCallable(() -> {
            PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
            return outwardRepo.findFiltered(warehouseId, status, dateFrom, dateTo, pr)
                    .map(OutwardMapper::toResponse);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<OutwardResponse> getById(UUID id) {
        return Mono.fromCallable(() -> {
            OutwardTransaction tx = outwardRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("OutwardTransaction", id));
            return OutwardMapper.toResponse(tx);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<OutwardResponse> create(OutwardRequest request) {
        return Mono.fromCallable(() -> {
            // Validate stock availability
            if (request.quantityBags() != null && request.quantityBags() > 0) {
                checkStock(request.warehouseId(), request.commodityName(),
                        BigDecimal.valueOf(request.quantityBags()));
            }
            OutwardTransaction tx = OutwardMapper.toEntity(request);
            tx = outwardRepo.save(tx);
            log.info("OutwardTransaction created id={}", tx.getId());
            return OutwardMapper.toResponse(tx);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<OutwardResponse> approve(UUID id, String approvedByUserId) {
        // TransactionTemplate runs the transaction on the same boundedElastic thread as the DB work.
        // Any exception from checkStock or deductStock rolls back updateApproval atomically.
        return Mono.fromCallable(() -> txTemplate.execute(status -> {
            OutwardTransaction tx = outwardRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("OutwardTransaction", id));

            BigDecimal qty = tx.getQuantityBags() != null
                    ? BigDecimal.valueOf(tx.getQuantityBags()) : tx.getQuantity();

            checkStock(tx.getWarehouseId(), tx.getItemName(), qty);

            UUID approvedBy = UUID.fromString(approvedByUserId);
            outwardRepo.updateApproval(id, "APPROVED", approvedBy);
            deductStock(tx.getWarehouseId(), tx.getItemName(), qty);

            tx.setStatus("APPROVED");
            tx.setApprovedBy(approvedBy);
            return OutwardMapper.toResponse(tx);
        })).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<OutwardResponse> reject(UUID id, String reason) {
        return Mono.fromCallable(() -> {
            OutwardTransaction tx = outwardRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("OutwardTransaction", id));
            tx.setStatus("REJECTED");
            tx.setRemarks(reason);
            tx = outwardRepo.save(tx);
            return OutwardMapper.toResponse(tx);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return Mono.fromRunnable(() -> {
            if (!outwardRepo.existsById(id)) throw new EntityNotFoundException("OutwardTransaction", id);
            outwardRepo.deleteById(id);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private void checkStock(String warehouseId, String itemName, BigDecimal required) {
        StockInventory stock = stockRepo
                .findByWarehouseIdAndItemNameIgnoreCase(warehouseId, itemName)
                .orElse(null);

        BigDecimal currentStock = (stock != null && stock.getCurrentStock() != null)
                ? stock.getCurrentStock() : BigDecimal.ZERO;
        if (currentStock.compareTo(required) < 0) {
            throw new InsufficientStockException(itemName,
                    currentStock.doubleValue(), required.doubleValue());
        }
    }

    private void deductStock(String warehouseId, String itemName, BigDecimal qty) {
        stockRepo.findByWarehouseIdAndItemNameIgnoreCase(warehouseId, itemName)
                .ifPresent(s -> {
                    BigDecimal current = s.getCurrentStock() != null
                            ? s.getCurrentStock() : BigDecimal.ZERO;
                    s.setCurrentStock(current.subtract(qty).max(BigDecimal.ZERO));
                    stockRepo.save(s);
                });
    }
}
