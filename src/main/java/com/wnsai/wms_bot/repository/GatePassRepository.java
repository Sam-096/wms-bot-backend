package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.GatePass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GatePassRepository extends JpaRepository<GatePass, UUID> {

    List<GatePass> findByWarehouseId(String warehouseId);

    List<GatePass> findByWarehouseIdAndStatus(String warehouseId, String status);

    long countByWarehouseIdAndStatus(String warehouseId, String status);
}
