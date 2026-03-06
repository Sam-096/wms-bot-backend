package com.wnsai.wms_bot.service.bond.impl;

import com.wnsai.wms_bot.constants.AppConstants;
import com.wnsai.wms_bot.dto.bond.BondRequest;
import com.wnsai.wms_bot.dto.bond.BondResponse;
import com.wnsai.wms_bot.entity.Bond;
import com.wnsai.wms_bot.exception.EntityNotFoundException;
import com.wnsai.wms_bot.mapper.BondMapper;
import com.wnsai.wms_bot.repository.BondRepository;
import com.wnsai.wms_bot.service.bond.BondService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BondServiceImpl implements BondService {

    private final BondRepository bondRepo;

    @Override
    public Mono<Page<BondResponse>> list(String warehouseId, int page, int size) {
        return Mono.fromCallable(() -> {
            PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
            List<Bond> all = bondRepo.findByWarehouseId(warehouseId);
            int start = (int) pr.getOffset();
            int end   = Math.min(start + size, all.size());
            List<BondResponse> content = all.subList(start, end)
                    .stream().map(BondMapper::toResponse).collect(Collectors.toList());
            return (Page<BondResponse>) new PageImpl<>(content, pr, all.size());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<BondResponse> getById(UUID id) {
        return Mono.fromCallable(() -> {
            Bond bond = bondRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Bond", id));
            return BondMapper.toResponse(bond);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<BondResponse> create(BondRequest request) {
        return Mono.fromCallable(() -> {
            Bond bond = BondMapper.toEntity(request);
            bond = bondRepo.save(bond);
            log.info("Bond created id={}", bond.getId());
            return BondMapper.toResponse(bond);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<BondResponse> update(UUID id, BondRequest request) {
        return Mono.fromCallable(() -> {
            Bond bond = bondRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Bond", id));
            bond.setItemName(request.itemName());
            bond.setQuantity(request.quantity());
            bond.setBondDate(request.bondDate());
            bond.setExpiryDate(request.expiryDate());
            bond = bondRepo.save(bond);
            return BondMapper.toResponse(bond);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<BondResponse> release(UUID id) {
        return Mono.fromCallable(() -> {
            Bond bond = bondRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Bond", id));
            bond.setStatus(AppConstants.BOND_STATUS_CLOSED);
            bond = bondRepo.save(bond);
            log.info("Bond released id={}", id);
            return BondMapper.toResponse(bond);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<BondResponse>> getActive(String warehouseId) {
        return Mono.fromCallable(() ->
            bondRepo.findByWarehouseIdAndStatus(warehouseId, AppConstants.BOND_STATUS_ACTIVE)
                    .stream().map(BondMapper::toResponse).collect(Collectors.toList())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<BondResponse>> getExpiring(String warehouseId) {
        return Mono.fromCallable(() -> {
            LocalDate from = LocalDate.now();
            LocalDate to   = from.plusDays(AppConstants.BOND_EXPIRY_LOOKAHEAD_DAYS);
            return bondRepo.findExpiringBonds(warehouseId, from, to)
                    .stream().map(BondMapper::toResponse).collect(Collectors.toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
