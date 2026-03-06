package com.wnsai.wms_bot.service.report;

import com.wnsai.wms_bot.dto.report.ReportRequest;
import com.wnsai.wms_bot.dto.report.ReportResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface ReportService {
    Mono<ReportResponse>       generate(ReportRequest request, String userId);
    Mono<ReportResponse>       getStatus(UUID reportId);
    Mono<byte[]>               download(UUID reportId);
    Mono<List<ReportResponse>> getHistory(String warehouseId);
}
