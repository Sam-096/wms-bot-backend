package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.Bond;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface BondRepository extends JpaRepository<Bond, UUID> {

    List<Bond> findByWarehouseId(String warehouseId);

    List<Bond> findByWarehouseIdAndStatus(String warehouseId, String status);

    long countByWarehouseIdAndStatus(String warehouseId, String status);

    /** Bonds expiring within a given date range (e.g. next 7 days). */
    @Query("SELECT b FROM Bond b " +
           "WHERE b.warehouseId = :warehouseId " +
           "AND b.status = 'ACTIVE' " +
           "AND b.expiryDate BETWEEN :from AND :to " +
           "ORDER BY b.expiryDate ASC")
    List<Bond> findExpiringBonds(@Param("warehouseId") String warehouseId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);
}
