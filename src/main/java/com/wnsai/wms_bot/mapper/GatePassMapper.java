package com.wnsai.wms_bot.mapper;

import com.wnsai.wms_bot.dto.gatepass.GatePassRequest;
import com.wnsai.wms_bot.dto.gatepass.GatePassResponse;
import com.wnsai.wms_bot.entity.GatePass;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class GatePassMapper {

    private GatePassMapper() {}

    public static GatePass toEntity(GatePassRequest req, String operatorUserId) {
        return GatePass.builder()
                .warehouseId(req.warehouseId())
                .vehicleNumber(req.vehicleNumber())
                .driverName(req.driverName())
                .passType(req.purpose())
                .purpose(req.purpose())
                .commodityName(req.commodityName())
                .bagsCount(req.bagsCount())
                .status("OPEN")
                .entryTime(OffsetDateTime.now())
                .operatorId(operatorUserId != null ? UUID.fromString(operatorUserId) : null)
                .inwardTransactionId(req.inwardTransactionId() != null
                        ? UUID.fromString(req.inwardTransactionId()) : null)
                .outwardTransactionId(req.outwardTransactionId() != null
                        ? UUID.fromString(req.outwardTransactionId()) : null)
                .build();
    }

    public static GatePassResponse toResponse(GatePass e) {
        Long duration = null;
        if (e.getEntryTime() != null && e.getExitTime() != null) {
            duration = Duration.between(e.getEntryTime(), e.getExitTime()).toMinutes();
        }
        return new GatePassResponse(
                e.getId(),
                e.getWarehouseId(),
                e.getPassNumber(),
                e.getVehicleNumber(),
                e.getDriverName(),
                e.getPurpose() != null ? e.getPurpose() : e.getPassType(),
                e.getCommodityName(),
                e.getBagsCount(),
                e.getStatus(),
                e.getEntryTime(),
                e.getExitTime(),
                e.getOperatorId(),
                e.getInwardTransactionId(),
                e.getOutwardTransactionId(),
                e.getCreatedAt(),
                duration
        );
    }
}
