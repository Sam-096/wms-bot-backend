package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.StockInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockInventoryRepository extends JpaRepository<StockInventory, UUID> {

    List<StockInventory> findByWarehouseId(String warehouseId);

    /**
     * Items where current_stock has fallen at or below min_threshold.
     * Uses JPQL to compare two fields on the same row — not expressible
     * as a plain Spring Data method name.
     */
    @Query("SELECT s FROM StockInventory s " +
           "WHERE s.warehouseId = :warehouseId " +
           "AND s.currentStock <= s.minThreshold " +
           "ORDER BY s.currentStock ASC")
    List<StockInventory> findLowStockItems(@Param("warehouseId") String warehouseId);
}
