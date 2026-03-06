package com.wnsai.wms_bot.service.outward;

import com.wnsai.wms_bot.dto.outward.OutwardRequest;
import com.wnsai.wms_bot.dto.outward.OutwardResponse;
import org.springframework.data.domain.Page;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface OutwardService {
    Mono<Page<OutwardResponse>> list(String warehouseId, String status,
                                     LocalDate dateFrom, LocalDate dateTo,
                                     int page, int size);
    Mono<OutwardResponse> getById(UUID id);
    Mono<OutwardResponse> create(OutwardRequest request);
    Mono<OutwardResponse> approve(UUID id, String approvedByUserId);
    Mono<OutwardResponse> reject(UUID id, String reason);
    Mono<Void>            delete(UUID id);
}
