package com.wnsai.wms_bot.dto.inventory;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record InventoryUpdateRequest(
    String itemCode,
    @DecimalMin(value = "0", message = "Min threshold cannot be negative")
    BigDecimal minThreshold,
    String unit
) {}
