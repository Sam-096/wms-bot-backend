package com.wnsai.wms_bot.controller;

import com.wnsai.wms_bot.dto.outward.OutwardRequest;
import com.wnsai.wms_bot.dto.outward.OutwardResponse;
import com.wnsai.wms_bot.service.outward.OutwardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/outward")
@RequiredArgsConstructor
public class OutwardController {

    private final OutwardService outwardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<Page<OutwardResponse>> list(
            @RequestParam(required = false)                          String warehouseId,
            @RequestParam(required = false)                          String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0")                        int page,
            @RequestParam(defaultValue = "20")                       int size) {
        return outwardService.list(warehouseId, status, dateFrom, dateTo, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<OutwardResponse> getById(@PathVariable UUID id) {
        return outwardService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<OutwardResponse> create(@Valid @RequestBody OutwardRequest request) {
        return outwardService.create(request);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public Mono<OutwardResponse> approve(@PathVariable UUID id,
                                          @AuthenticationPrincipal String userId) {
        return outwardService.approve(id, userId);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<OutwardResponse> reject(@PathVariable UUID id,
                                         @RequestParam(defaultValue = "") String reason) {
        return outwardService.reject(id, reason);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    public Mono<Void> delete(@PathVariable UUID id) {
        return outwardService.delete(id);
    }
}
