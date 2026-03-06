package com.wnsai.wms_bot.controller;

import com.wnsai.wms_bot.dto.report.ReportRequest;
import com.wnsai.wms_bot.dto.report.ReportResponse;
import com.wnsai.wms_bot.service.report.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * POST /api/v1/reports/generate
     * Queues async report generation. Returns immediately with GENERATING status + reportId.
     * Poll GET /{reportId}/status until status=READY, then download.
     */
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','VIEWER')")
    public Mono<ReportResponse> generate(
            @Valid @RequestBody ReportRequest request,
            @AuthenticationPrincipal String userId) {
        return reportService.generate(request, userId);
    }

    /**
     * GET /api/v1/reports/{reportId}/status
     * Poll until status transitions from GENERATING → READY or FAILED.
     */
    @GetMapping("/{reportId}/status")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','VIEWER')")
    public Mono<ReportResponse> getStatus(@PathVariable UUID reportId) {
        return reportService.getStatus(reportId);
    }

    /**
     * GET /api/v1/reports/{reportId}/download
     * Streams the generated file (CSV or PDF).
     * Report must be READY; returns 400 if still generating.
     */
    @GetMapping("/{reportId}/download")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','VIEWER')")
    public Mono<ResponseEntity<Resource>> download(@PathVariable UUID reportId) {
        return reportService.getStatus(reportId)
                .flatMap(meta -> reportService.download(reportId)
                        .map(bytes -> {
                            String filename = "report_" + reportId + "." + meta.format().toLowerCase();
                            MediaType contentType = "PDF".equalsIgnoreCase(meta.format())
                                    ? MediaType.APPLICATION_PDF
                                    : MediaType.parseMediaType("text/csv");

                            return ResponseEntity.ok()
                                    .header(HttpHeaders.CONTENT_DISPOSITION,
                                            "attachment; filename=\"" + filename + "\"")
                                    .contentType(contentType)
                                    .contentLength(bytes.length)
                                    .<Resource>body(new ByteArrayResource(bytes));
                        })
                );
    }

    /**
     * GET /api/v1/reports/history?warehouseId=WH001
     * Returns last 20 reports for the warehouse (all statuses).
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('MANAGER','OPERATOR','VIEWER')")
    public Mono<List<ReportResponse>> getHistory(@RequestParam String warehouseId) {
        return reportService.getHistory(warehouseId);
    }
}
