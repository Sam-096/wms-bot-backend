package com.wnsai.wms_bot.dto.bond;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BondRequest(

    @NotBlank(message = "Warehouse ID is required")
    @Size(max = 50)
    String warehouseId,

    @NotBlank(message = "Bond number is required")
    String bondNumber,

    @NotBlank(message = "Item name is required")
    @Size(max = 200)
    String itemName,

    @DecimalMin(value = "0.001", message = "Quantity must be positive")
    BigDecimal quantity,

    @NotNull(message = "Bond date is required")
    LocalDate bondDate,

    @NotNull(message = "Expiry date is required")
    @Future(message = "Expiry date must be in the future")
    LocalDate expiryDate
) {}
