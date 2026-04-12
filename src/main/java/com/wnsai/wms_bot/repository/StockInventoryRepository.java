package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.StockInventory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockInventoryRepository extends JpaRepository<StockInventory, UUID> {

    List<StockInventory> findByWarehouseId(String warehouseId);

    Optional<StockInventory> findByWarehouseIdAndItemNameIgnoreCase(String warehouseId, String itemName);

    /** All items below min_threshold — used by inventory summary, reports, dashboard. */
    @Query("SELECT s FROM StockInventory s " +
           "WHERE s.warehouseId = :warehouseId " +
           "AND s.currentStock <= s.minThreshold " +
           "ORDER BY s.currentStock ASC")
    List<StockInventory> findLowStockItems(@Param("warehouseId") String warehouseId);

    /** Top-N items below min_threshold — used by AI prompt context (capped at 10). */
    @Query("SELECT s FROM StockInventory s " +
           "WHERE s.warehouseId = :warehouseId " +
           "AND s.currentStock <= s.minThreshold " +
           "ORDER BY s.currentStock ASC")
    List<StockInventory> findLowStockItems(@Param("warehouseId") String warehouseId, Pageable pageable);
}
