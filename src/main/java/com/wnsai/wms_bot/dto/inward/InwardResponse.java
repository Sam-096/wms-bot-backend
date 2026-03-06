package com.wnsai.wms_bot.dto.inward;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InwardResponse(
    UUID           id,
    String         warehouseId,
    String         grnNumber,
    String         commodityName,
    String         supplierName,
    String         vehicleNumber,
    Integer        quantityBags,
    BigDecimal     unitWeight,
    BigDecimal     totalWeight,
    String         unit,
    String         status,
    String         remarks,
    LocalDate      inwardDate,
    UUID           approvedBy,
    OffsetDateTime approvedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
