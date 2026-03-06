package com.wnsai.wms_bot.mapper;

import com.wnsai.wms_bot.dto.bond.BondRequest;
import com.wnsai.wms_bot.dto.bond.BondResponse;
import com.wnsai.wms_bot.entity.Bond;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class BondMapper {

    private BondMapper() {}

    public static Bond toEntity(BondRequest req) {
        return Bond.builder()
                .warehouseId(req.warehouseId())
                .bondNumber(req.bondNumber())
                .itemName(req.itemName())
                .quantity(req.quantity())
                .bondDate(req.bondDate())
                .expiryDate(req.expiryDate())
                .status("ACTIVE")
                .build();
    }

    public static BondResponse toResponse(Bond e) {
        long daysUntilExpiry = e.getExpiryDate() != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), e.getExpiryDate())
                : 0L;
        return new BondResponse(
                e.getId(),
                e.getWarehouseId(),
                e.getBondNumber(),
                e.getItemName(),
                e.getQuantity(),
                e.getBondDate(),
                e.getExpiryDate(),
                e.getStatus(),
                daysUntilExpiry,
                e.getCreatedAt()
        );
    }
}
