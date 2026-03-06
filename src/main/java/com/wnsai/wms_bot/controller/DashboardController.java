package com.wnsai.wms_bot.controller;

import com.wnsai.wms_bot.dto.dashboard.SnapshotResponse;
import com.wnsai.wms_bot.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/v1/dashboard/snapshot?warehouseId={id}
     * Cached for 60 seconds per warehouse.
     */
    @GetMapping("/snapshot")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','GATE_STAFF','VIEWER')")
    public Mono<SnapshotResponse> getSnapshot(@RequestParam String warehouseId) {
        log.debug("GET /api/v1/dashboard/snapshot warehouseId={}", warehouseId);
        return dashboardService.getSnapshot(warehouseId);
    }
}
