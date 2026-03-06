package com.wnsai.wms_bot.service.bond;

import com.wnsai.wms_bot.dto.bond.BondRequest;
import com.wnsai.wms_bot.dto.bond.BondResponse;
import org.springframework.data.domain.Page;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface BondService {
    Mono<Page<BondResponse>> list(String warehouseId, int page, int size);
    Mono<BondResponse>       getById(UUID id);
    Mono<BondResponse>       create(BondRequest request);
    Mono<BondResponse>       update(UUID id, BondRequest request);
    Mono<BondResponse>       release(UUID id);
    Mono<List<BondResponse>> getActive(String warehouseId);
    Mono<List<BondResponse>> getExpiring(String warehouseId);
}
