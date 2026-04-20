package com.wnsai.wms_bot.controller;

import com.wnsai.wms_bot.dto.inward.InwardRequest;
import com.wnsai.wms_bot.dto.inward.InwardResponse;
import com.wnsai.wms_bot.service.inward.InwardService;
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
@RequestMapping("/api/v1/inward")
@RequiredArgsConstructor
public class InwardController {

    private final InwardService inwardService;

    /** GET /api/v1/inward?warehouseId=&status=&dateFrom=&dateTo=&page=0&size=20 */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<Page<InwardResponse>> list(
            @RequestParam(required = false)                          String warehouseId,
            @RequestParam(required = false)                          String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0")                        int page,
            @RequestParam(defaultValue = "20")                       int size) {
        log.debug("GET /api/v1/inward warehouseId={}", warehouseId);
        return inwardService.list(warehouseId, status, dateFrom, dateTo, page, size);
    }

    /** GET /api/v1/inward/{id} */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<InwardResponse> getById(@PathVariable UUID id) {
        log.debug("GET /api/v1/inward/{}", id);
        return inwardService.getById(id);
    }

    /** POST /api/v1/inward */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<InwardResponse> create(@Valid @RequestBody InwardRequest request) {
        log.debug("POST /api/v1/inward warehouseId={}", request.warehouseId());
        return inwardService.create(request);
    }

    /** PUT /api/v1/inward/{id} */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<InwardResponse> update(@PathVariable UUID id,
                                        @Valid @RequestBody InwardRequest request) {
        log.debug("PUT /api/v1/inward/{}", id);
        return inwardService.update(id, request);
    }

    /** PUT /api/v1/inward/{id}/approve — MANAGER only */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public Mono<InwardResponse> approve(@PathVariable UUID id,
                                         @AuthenticationPrincipal String userId) {
        log.debug("PUT /api/v1/inward/{}/approve by={}", id, userId);
        return inwardService.approve(id, userId);
    }

    /** PUT /api/v1/inward/{id}/reject */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR')")
    public Mono<InwardResponse> reject(@PathVariable UUID id,
                                        @RequestParam(defaultValue = "") String reason) {
        log.debug("PUT /api/v1/inward/{}/reject", id);
        return inwardService.reject(id, reason);
    }

    /** DELETE /api/v1/inward/{id} */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    public Mono<Void> delete(@PathVariable UUID id) {
        log.debug("DELETE /api/v1/inward/{}", id);
        return inwardService.delete(id);
    }
}
