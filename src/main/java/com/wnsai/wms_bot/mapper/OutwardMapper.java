package com.wnsai.wms_bot.mapper;

import com.wnsai.wms_bot.dto.outward.OutwardRequest;
import com.wnsai.wms_bot.dto.outward.OutwardResponse;
import com.wnsai.wms_bot.entity.OutwardTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class OutwardMapper {

    private OutwardMapper() {}

    public static OutwardTransaction toEntity(OutwardRequest req) {
        BigDecimal bags  = req.quantityBags() != null ? BigDecimal.valueOf(req.quantityBags()) : BigDecimal.ZERO;
        BigDecimal wt    = req.unitWeight()   != null ? req.unitWeight() : BigDecimal.ZERO;
        BigDecimal total = bags.multiply(wt);

        return OutwardTransaction.builder()
                .warehouseId(req.warehouseId())
                .itemName(req.commodityName())
                .customerName(req.customerName())
                .vehicleNumber(req.vehicleNumber())
                .dispatchNumber(req.dispatchNumber())
                .quantityBags(req.quantityBags())
                .quantity(BigDecimal.valueOf(req.quantityBags() != null ? req.quantityBags() : 0))
                .unitWeight(req.unitWeight())
                .totalWeight(total)
                .unit(req.unit())
                .remarks(req.remarks())
                .outwardDate(req.outwardDate() != null ? req.outwardDate() : LocalDate.now())
                .status("PENDING")
                .build();
    }

    public static OutwardResponse toResponse(OutwardTransaction e) {
        return new OutwardResponse(
                e.getId(),
                e.getWarehouseId(),
                e.getDispatchNumber(),
                e.getItemName(),
                e.getCustomerName(),
                e.getVehicleNumber(),
                e.getQuantityBags(),
                e.getUnitWeight(),
                e.getTotalWeight(),
                e.getUnit(),
                e.getStatus(),
                e.getRemarks(),
                e.getOutwardDate(),
                e.getApprovedBy(),
                e.getApprovedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
