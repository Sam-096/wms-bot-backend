package com.wnsai.wms_bot.mapper;

import com.wnsai.wms_bot.dto.inward.InwardRequest;
import com.wnsai.wms_bot.dto.inward.InwardResponse;
import com.wnsai.wms_bot.entity.InwardTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class InwardMapper {

    private InwardMapper() {}

    public static InwardTransaction toEntity(InwardRequest req) {
        BigDecimal bags  = req.quantityBags() != null ? BigDecimal.valueOf(req.quantityBags()) : BigDecimal.ZERO;
        BigDecimal wt    = req.unitWeight()   != null ? req.unitWeight() : BigDecimal.ZERO;
        BigDecimal total = bags.multiply(wt);

        return InwardTransaction.builder()
                .warehouseId(req.warehouseId())
                .itemName(req.commodityName())
                .supplierName(req.supplierName())
                .vehicleNumber(req.vehicleNumber())
                .grnNumber(req.grnNumber())
                .quantityBags(req.quantityBags())
                .quantity(BigDecimal.valueOf(req.quantityBags() != null ? req.quantityBags() : 0))
                .unitWeight(req.unitWeight())
                .totalWeight(total)
                .unit(req.unit())
                .remarks(req.remarks())
                .inwardDate(req.inwardDate() != null ? req.inwardDate() : LocalDate.now())
                .status("PENDING")
                .build();
    }

    public static InwardResponse toResponse(InwardTransaction e) {
        return new InwardResponse(
                e.getId(),
                e.getWarehouseId(),
                e.getGrnNumber(),
                e.getItemName(),
                e.getSupplierName(),
                e.getVehicleNumber(),
                e.getQuantityBags(),
                e.getUnitWeight(),
                e.getTotalWeight(),
                e.getUnit(),
                e.getStatus(),
                e.getRemarks(),
                e.getInwardDate(),
                e.getApprovedBy(),
                e.getApprovedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
