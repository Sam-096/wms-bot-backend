package com.wnsai.wms_bot.dto.outward;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OutwardRequest(

    @NotBlank(message = "Warehouse ID is required")
    @Size(max = 50)
    String warehouseId,

    @NotBlank(message = "Commodity name is required")
    @Size(max = 200)
    String commodityName,

    String customerName,

    @Size(max = 30)
    String vehicleNumber,

    String dispatchNumber,

    @Positive(message = "Quantity bags must be positive")
    Integer quantityBags,

    @DecimalMin(value = "0.001", message = "Unit weight must be positive")
    BigDecimal unitWeight,

    String unit,

    @Size(max = 1000)
    String remarks,

    LocalDate outwardDate
) {}
