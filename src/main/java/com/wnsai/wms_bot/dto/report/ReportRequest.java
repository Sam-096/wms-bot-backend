package com.wnsai.wms_bot.dto.report;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record ReportRequest(

    @NotBlank(message = "Report type is required")
    @Pattern(regexp = "^(STOCK_SUMMARY|INWARD_SUMMARY|OUTWARD_SUMMARY|GATE_PASS_LOG|BOND_STATUS|DAILY_ACTIVITY)$",
             message = "Invalid report type")
    String reportType,

    @NotBlank(message = "Warehouse ID is required")
    @Size(max = 50)
    String warehouseId,

    LocalDate dateFrom,

    LocalDate dateTo,

    @NotBlank(message = "Format is required")
    @Pattern(regexp = "^(CSV|PDF)$", message = "Format must be CSV or PDF")
    String format
) {}
