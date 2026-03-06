package com.wnsai.wms_bot.controller;

import com.wnsai.wms_bot.dto.bond.BondRequest;
import com.wnsai.wms_bot.dto.bond.BondResponse;
import com.wnsai.wms_bot.service.bond.BondService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/bonds")
@RequiredArgsConstructor
public class BondController {

    private final BondService bondService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','VIEWER')")
    public Mono<Page<BondResponse>> list(
            @RequestParam                      String warehouseId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return bondService.list(warehouseId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','VIEWER')")
    public Mono<BondResponse> getById(@PathVariable UUID id) {
        return bondService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public Mono<BondResponse> create(@Valid @RequestBody BondRequest request) {
        return bondService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public Mono<BondResponse> update(@PathVariable UUID id,
                                      @Valid @RequestBody BondRequest request) {
        return bondService.update(id, request);
    }

    @PutMapping("/{id}/release")
    @PreAuthorize("hasRole('MANAGER')")
    public Mono<BondResponse> release(@PathVariable UUID id) {
        return bondService.release(id);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('MANAGER','VIEWER')")
    public Mono<List<BondResponse>> getActive(@RequestParam String warehouseId) {
        return bondService.getActive(warehouseId);
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('MANAGER','VIEWER')")
    public Mono<List<BondResponse>> getExpiring(@RequestParam String warehouseId) {
        return bondService.getExpiring(warehouseId);
    }
}
