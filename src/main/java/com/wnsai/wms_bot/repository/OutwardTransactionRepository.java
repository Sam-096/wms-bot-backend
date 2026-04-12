package com.wnsai.wms_bot.repository;

import com.wnsai.wms_bot.entity.OutwardTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutwardTransactionRepository extends JpaRepository<OutwardTransaction, UUID> {

    List<OutwardTransaction> findByWarehouseId(String warehouseId);

    Page<OutwardTransaction> findByWarehouseId(String warehouseId, Pageable pageable);

    List<OutwardTransaction> findByWarehouseIdAndStatus(String warehouseId, String status);

    long countByWarehouseIdAndStatus(String warehouseId, String status);

    List<OutwardTransaction> findTop10ByWarehouseIdOrderByCreatedAtDesc(String warehouseId);

    @Query("SELECT o FROM OutwardTransaction o WHERE o.warehouseId = :wid " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:dateFrom IS NULL OR o.outwardDate >= :dateFrom) " +
           "AND (:dateTo IS NULL OR o.outwardDate <= :dateTo)")
    Page<OutwardTransaction> findFiltered(
            @Param("wid")      String warehouseId,
            @Param("status")   String status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo")   LocalDate dateTo,
            Pageable pageable);

    /** Count outward transactions APPROVED today (uses approvedAt timestamp, not planned outwardDate). */
    @Query("SELECT COUNT(o) FROM OutwardTransaction o WHERE o.warehouseId = :wid " +
           "AND o.status = 'APPROVED' AND o.approvedAt IS NOT NULL " +
           "AND CAST(o.approvedAt AS LocalDate) = :today")
    long countTodayDispatched(@Param("wid") String warehouseId, @Param("today") LocalDate today);

    @Modifying
    @Transactional
    @Query("UPDATE OutwardTransaction o SET o.status = :status, o.approvedBy = :approvedBy, " +
           "o.approvedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    void updateApproval(@Param("id") UUID id, @Param("status") String status, @Param("approvedBy") UUID approvedBy);
}
