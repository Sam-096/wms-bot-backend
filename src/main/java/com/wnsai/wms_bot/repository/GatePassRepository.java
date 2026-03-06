package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.GatePass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface GatePassRepository extends JpaRepository<GatePass, UUID> {

    List<GatePass> findByWarehouseId(String warehouseId);

    Page<GatePass> findByWarehouseId(String warehouseId, Pageable pageable);

    List<GatePass> findByWarehouseIdAndStatus(String warehouseId, String status);

    Page<GatePass> findByWarehouseIdAndStatus(String warehouseId, String status, Pageable pageable);

    long countByWarehouseIdAndStatus(String warehouseId, String status);

    /** Vehicles currently inside (OPEN status) */
    @Query("SELECT g FROM GatePass g WHERE g.warehouseId = :wid AND g.status = 'OPEN'")
    List<GatePass> findActiveByWarehouseId(@Param("wid") String warehouseId);

    /** Vehicles inside longer than the given threshold (overstay) */
    @Query("SELECT g FROM GatePass g WHERE g.warehouseId = :wid AND g.status = 'OPEN' " +
           "AND g.entryTime < :threshold")
    List<GatePass> findOverstayByWarehouseId(
            @Param("wid") String warehouseId,
            @Param("threshold") OffsetDateTime threshold);
}
