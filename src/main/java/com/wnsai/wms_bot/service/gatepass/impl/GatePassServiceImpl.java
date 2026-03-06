package com.wnsai.wms_bot.service.gatepass.impl;

import com.wnsai.wms_bot.constants.AppConstants;
import com.wnsai.wms_bot.dto.gatepass.GatePassRequest;
import com.wnsai.wms_bot.dto.gatepass.GatePassResponse;
import com.wnsai.wms_bot.entity.GatePass;
import com.wnsai.wms_bot.exception.EntityNotFoundException;
import com.wnsai.wms_bot.mapper.GatePassMapper;
import com.wnsai.wms_bot.repository.GatePassRepository;
import com.wnsai.wms_bot.service.gatepass.GatePassService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatePassServiceImpl implements GatePassService {

    private final GatePassRepository gatePassRepo;

    @Override
    public Mono<Page<GatePassResponse>> list(String warehouseId, String status, int page, int size) {
        return Mono.fromCallable(() -> {
            PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<GatePass> result = status != null && !status.isBlank()
                    ? gatePassRepo.findByWarehouseIdAndStatus(warehouseId, status, pr)
                    : gatePassRepo.findByWarehouseId(warehouseId, pr);
            return result.map(GatePassMapper::toResponse);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<GatePassResponse> getById(UUID id) {
        return Mono.fromCallable(() -> {
            GatePass gp = gatePassRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("GatePass", id));
            return GatePassMapper.toResponse(gp);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<GatePassResponse> create(GatePassRequest request, String operatorUserId) {
        return Mono.fromCallable(() -> {
            GatePass gp = GatePassMapper.toEntity(request, operatorUserId);
            gp = gatePassRepo.save(gp);
            log.info("GatePass created id={} vehicle={}", gp.getId(), gp.getVehicleNumber());
            return GatePassMapper.toResponse(gp);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<GatePassResponse> close(UUID id) {
        return Mono.fromCallable(() -> {
            GatePass gp = gatePassRepo.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("GatePass", id));
            gp.setStatus(AppConstants.GATE_STATUS_CLOSED);
            gp.setExitTime(OffsetDateTime.now());
            gp = gatePassRepo.save(gp);
            log.info("GatePass closed id={}", id);
            return GatePassMapper.toResponse(gp);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<GatePassResponse>> getActive(String warehouseId) {
        return Mono.fromCallable(() ->
            gatePassRepo.findActiveByWarehouseId(warehouseId)
                    .stream().map(GatePassMapper::toResponse).collect(Collectors.toList())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<GatePassResponse>> getOverstay(String warehouseId) {
        return Mono.fromCallable(() -> {
            OffsetDateTime threshold = OffsetDateTime.now()
                    .minusHours(AppConstants.GATE_OVERSTAY_HOURS);
            return gatePassRepo.findOverstayByWarehouseId(warehouseId, threshold)
                    .stream().map(GatePassMapper::toResponse).collect(Collectors.toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
