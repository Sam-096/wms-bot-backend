package com.wnsai.wms_bot.controller;

import com.wnsai.wms_bot.dto.gatepass.GatePassRequest;
import com.wnsai.wms_bot.dto.gatepass.GatePassResponse;
import com.wnsai.wms_bot.service.gatepass.GatePassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/gate-pass")
@RequiredArgsConstructor
public class GatePassController {

    private final GatePassService gatePassService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','GATE_STAFF')")
    public Mono<Page<GatePassResponse>> list(
            @RequestParam                    String warehouseId,
            @RequestParam(required = false)  String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return gatePassService.list(warehouseId, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','GATE_STAFF')")
    public Mono<GatePassResponse> getById(@PathVariable UUID id) {
        return gatePassService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','GATE_STAFF')")
    public Mono<GatePassResponse> create(@Valid @RequestBody GatePassRequest request,
                                          @AuthenticationPrincipal String userId) {
        return gatePassService.create(request, userId);
    }

    @PutMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','GATE_STAFF')")
    public Mono<GatePassResponse> close(@PathVariable UUID id) {
        return gatePassService.close(id);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','GATE_STAFF')")
    public Mono<List<GatePassResponse>> getActive(@RequestParam String warehouseId) {
        return gatePassService.getActive(warehouseId);
    }

    @GetMapping("/overstay")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','GATE_STAFF')")
    public Mono<List<GatePassResponse>> getOverstay(@RequestParam String warehouseId) {
        return gatePassService.getOverstay(warehouseId);
    }
}
