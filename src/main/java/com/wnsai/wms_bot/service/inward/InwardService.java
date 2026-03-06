package com.wnsai.wms_bot.service.inward;

import com.wnsai.wms_bot.dto.inward.InwardRequest;
import com.wnsai.wms_bot.dto.inward.InwardResponse;
import org.springframework.data.domain.Page;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface InwardService {

    Mono<Page<InwardResponse>> list(String warehouseId, String status,
                                    LocalDate dateFrom, LocalDate dateTo,
                                    int page, int size);

    Mono<InwardResponse> getById(UUID id);

    Mono<InwardResponse> create(InwardRequest request);

    Mono<InwardResponse> update(UUID id, InwardRequest request);

    Mono<InwardResponse> approve(UUID id, String approvedByUserId);

    Mono<InwardResponse> reject(UUID id, String reason);

    Mono<Void> delete(UUID id);
}
