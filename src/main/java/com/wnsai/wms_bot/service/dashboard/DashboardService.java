package com.wnsai.wms_bot.service.dashboard;

import com.wnsai.wms_bot.dto.dashboard.SnapshotResponse;
import reactor.core.publisher.Mono;

public interface DashboardService {
    Mono<SnapshotResponse> getSnapshot(String warehouseId);
}
