package com.wnsai.wms_bot.dto.outward;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OutwardResponse(
    UUID           id,
    String         warehouseId,
    String         dispatchNumber,
    String         commodityName,
    String         customerName,
    String         vehicleNumber,
    Integer        quantityBags,
    BigDecimal     unitWeight,
    BigDecimal     totalWeight,
    String         unit,
    String         status,
    String         remarks,
    LocalDate      outwardDate,
    UUID           approvedBy,
    OffsetDateTime approvedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
