package com.wnsai.wms_bot.dto.gatepass;

import jakarta.validation.constraints.*;

public record GatePassRequest(

    @NotBlank(message = "Warehouse ID is required")
    @Size(max = 50)
    String warehouseId,

    @NotBlank(message = "Vehicle number is required")
    @Size(max = 30)
    String vehicleNumber,

    @NotBlank(message = "Driver name is required")
    @Size(max = 200)
    String driverName,

    /** INWARD | OUTWARD | TRANSFER */
    @Pattern(regexp = "^(INWARD|OUTWARD|TRANSFER)$",
             message = "Purpose must be INWARD, OUTWARD, or TRANSFER")
    String purpose,

    @Size(max = 200)
    String commodityName,

    @Min(value = 0)
    Integer bagsCount,

    String inwardTransactionId,

    String outwardTransactionId
) {}
