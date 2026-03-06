package com.wnsai.wms_bot.dto.report;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportResponse(
    UUID           reportId,
    String         status,       // GENERATING | READY | FAILED
    String         reportType,
    String         format,
    String         warehouseId,
    OffsetDateTime generatedAt,
    OffsetDateTime expiresAt,
    String         errorMessage  // null unless FAILED
) {}
