package com.wnsai.wms_bot.service.gatepass;

import com.wnsai.wms_bot.dto.gatepass.GatePassRequest;
import com.wnsai.wms_bot.dto.gatepass.GatePassResponse;
import org.springframework.data.domain.Page;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface GatePassService {
    Mono<Page<GatePassResponse>> list(String warehouseId, String status, int page, int size);
    Mono<GatePassResponse>       getById(UUID id);
    Mono<GatePassResponse>       create(GatePassRequest request, String operatorUserId);
    Mono<GatePassResponse>       close(UUID id);
    Mono<List<GatePassResponse>> getActive(String warehouseId);
    Mono<List<GatePassResponse>> getOverstay(String warehouseId);
}
