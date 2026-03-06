package com.wnsai.wms_bot.dto.gatepass;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GatePassResponse(
    UUID           id,
    String         warehouseId,
    String         passNumber,
    String         vehicleNumber,
    String         driverName,
    String         purpose,
    String         commodityName,
    Integer        bagsCount,
    String         status,
    OffsetDateTime entryTime,
    OffsetDateTime exitTime,
    UUID           operatorId,
    UUID           inwardTransactionId,
    UUID           outwardTransactionId,
    OffsetDateTime createdAt,
    Long           durationMinutes   // null if not yet closed
) {}
