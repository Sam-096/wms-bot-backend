package com.wnsai.wms_bot.dto.bond;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BondResponse(
    UUID           id,
    String         warehouseId,
    String         bondNumber,
    String         itemName,
    BigDecimal     quantity,
    LocalDate      bondDate,
    LocalDate      expiryDate,
    String         status,
    long           daysUntilExpiry,
    OffsetDateTime createdAt
) {}
